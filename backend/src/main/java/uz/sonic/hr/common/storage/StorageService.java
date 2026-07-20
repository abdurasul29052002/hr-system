package uz.sonic.hr.common.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import uz.sonic.hr.common.config.S3Properties;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * S3-backed storage for task and comment attachments. Callers get back an opaque
 * object key to persist on the entity; {@link #publicUrl(String)} turns a key into a
 * browser-usable URL (the bucket / endpoint must allow public reads).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StorageService {

    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp");

    /**
     * Screen recordings are the main reason the size limit is 100MB — a bug report is far easier to act on
     * with a clip of it happening. Browsers/OS screen recorders produce mp4 (H.264), webm and, on Apple
     * devices, quicktime; "video/x-matroska" covers OBS-style .mkv captures.
     */
    private static final Set<String> ALLOWED_VIDEO_TYPES =
            Set.of("video/mp4", "video/webm", "video/quicktime", "video/x-matroska");

    /** Everything the browser uploads directly lands under this prefix — see {@link #presignUpload}. */
    public static final String DIRECT_PREFIX = "uploads";

    /** Hard ceiling for a direct upload, matching spring.servlet.multipart.max-file-size. */
    public static final long MAX_UPLOAD_BYTES = 100L * 1024 * 1024;

    private static final Duration PRESIGN_TTL = Duration.ofMinutes(30);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties props;

    /** A signed PUT the browser can upload to, plus the key to hand back when claiming the object. */
    public record PresignedUpload(String key, String uploadUrl) {
    }

    /** What S3 actually reports about a stored object — the only trustworthy source for size and type. */
    public record StoredObject(long size, String contentType) {
    }

    /**
     * Issues a short-lived signed PUT so the browser can upload straight to S3, bypassing our backend and
     * the Vercel proxy entirely. That is what makes 100MB screen recordings practical: no request body
     * crosses our server, so nothing holds a DB connection or trips the proxy's request timeout.
     *
     * <p>The key is built here, never taken from the client, and is namespaced by employee so a claim can
     * be checked against the caller. The signature covers the public-read ACL and the content type, so the
     * browser must send {@code x-amz-acl: public-read} and the same {@code Content-Type} it declared —
     * both must be permitted by the bucket's CORS "allowed headers".
     */
    public PresignedUpload presignUpload(Long employeeId, String fileName, String contentType) {
        requireEnabled();
        requireAllowedType(contentType);

        String key = DIRECT_PREFIX + "/" + employeeId + "/" + UUID.randomUUID() + "/" + uniqueName(fileName);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(props.getBucketName())
                .key(key)
                .acl(ObjectCannedACL.PUBLIC_READ)
                .contentType(contentType)
                .build();
        String url = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(PRESIGN_TTL)
                        .putObjectRequest(request)
                        .build())
                .url()
                .toString();
        return new PresignedUpload(key, url);
    }

    /**
     * Takes ownership of a directly-uploaded object: verifies it, then COPIES it to a final key under
     * {@code folder} and deletes the staging original.
     *
     * <p>The copy is the security boundary, not a tidiness measure. A presigned PUT is replayable until it
     * expires, so as long as an attachment points at the staging key its uploader can overwrite the bytes
     * afterwards — past the size limit, and after reviewers have already seen and approved the original.
     * The final key was never signed for anyone, so once copied the object is effectively immutable. It
     * also gives every claim its own object, so deleting one attachment cannot pull the file out from
     * under another, and it moves the file out of the prefix the orphan sweep scans.
     *
     * @return the final key plus the object's verified size and type
     */
    public FinalizedUpload finalizeUpload(String sourceKey, String folder, String originalFileName) {
        requireEnabled();
        StoredObject stored = requireUploaded(sourceKey);
        String finalKey = folder + "/" + UUID.randomUUID() + "/" + uniqueName(originalFileName);
        try {
            s3Client.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(props.getBucketName())
                    .sourceKey(sourceKey)
                    .destinationBucket(props.getBucketName())
                    .destinationKey(finalKey)
                    .acl(ObjectCannedACL.PUBLIC_READ)
                    .metadataDirective(MetadataDirective.COPY)
                    .build());
        } catch (S3Exception e) {
            log.error("S3 copy failed {} -> {}: {}", sourceKey, finalKey, e.awsErrorDetails().errorMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the upload", e);
        }
        // Best-effort: if this fails the object is merely left for the orphan sweep.
        delete(sourceKey);
        return new FinalizedUpload(finalKey, stored.size(), stored.contentType());
    }

    /** A claimed upload, now living at its permanent key. */
    public record FinalizedUpload(String key, long size, String contentType) {
    }

    /**
     * Confirms a directly-uploaded object exists, and reports its size and stored content type.
     *
     * <p>The SIZE here is authoritative — S3 measures it, so this is what enforces the 100MB limit on a
     * direct upload. The CONTENT TYPE is not independently verified: S3 stores verbatim whatever the PUT
     * declared, and that value is pinned by the signature to what was allow-listed at presign time. So the
     * type check bounds what the object can *claim* to be (never, say, text/html) but says nothing about
     * the actual bytes — treat it as a rendering hint, not as proof of file contents.
     */
    public StoredObject requireUploaded(String key) {
        requireEnabled();
        try {
            HeadObjectResponse head = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(props.getBucketName())
                    .key(key)
                    .build());
            long size = head.contentLength() != null ? head.contentLength() : 0L;
            if (size <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded file is empty");
            }
            if (size > MAX_UPLOAD_BYTES) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                        "File is too large. The limit is 100 MB per file.");
            }
            requireAllowedType(head.contentType());
            return new StoredObject(size, head.contentType());
        } catch (NoSuchKeyException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload not found — it may have expired");
        } catch (S3Exception e) {
            log.error("S3 head failed for key {}: {}", key, e.awsErrorDetails().errorMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not verify the upload", e);
        }
    }

    /**
     * Rejects a key that was not signed for this employee. Keys are minted in
     * {@code uploads/{employeeId}/{uuid}/{name}}, so the owner segment is enough to stop one user
     * attaching another user's freshly-uploaded file by guessing at it.
     */
    public void requireOwnedBy(String key, Long employeeId) {
        if (key == null || !key.startsWith(DIRECT_PREFIX + "/" + employeeId + "/")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "That upload does not belong to you");
        }
    }

    private void requireAllowedType(String contentType) {
        if (contentType == null
                || !(ALLOWED_IMAGE_TYPES.contains(contentType) || ALLOWED_VIDEO_TYPES.contains(contentType))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only images (jpg, png, gif, webp) and videos (mp4, webm, mov, mkv) are allowed");
        }
    }

    /**
     * Upload an image under {@code folder} (e.g. {@code "tasks/12"} or {@code "comments/5"}).
     *
     * @return the S3 object key to store on the entity
     */
    public String upload(MultipartFile file, String folder) {
        requireEnabled();
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot upload empty file");
        }
        String contentType = file.getContentType();
        if (contentType == null
                || !(ALLOWED_IMAGE_TYPES.contains(contentType) || ALLOWED_VIDEO_TYPES.contains(contentType))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only images (jpg, png, gif, webp) and videos (mp4, webm, mov, mkv) are allowed");
        }

        String key = folder + "/" + uniqueName(file.getOriginalFilename());
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(props.getBucketName())
                    .key(key)
                    // Objects are served via a direct (unsigned) public URL — see publicUrl().
                    // Mark each upload public-read so DigitalOcean Spaces / S3 serves it (default is private → 403).
                    .acl(ObjectCannedACL.PUBLIC_READ)
                    .contentType(contentType)
                    .contentLength(file.getSize())
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("Uploaded to S3: bucket={}, key={}", props.getBucketName(), key);
            return key;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read upload", e);
        } catch (S3Exception e) {
            log.error("S3 upload failed: {}", e.awsErrorDetails().errorMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "S3 upload failed", e);
        }
    }

    /** Delete an object by key. Best-effort: nulls and S3 errors are swallowed. */
    public void delete(String key) {
        if (key == null || !props.isEnabled()) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(props.getBucketName())
                    .key(key)
                    .build());
            log.info("Deleted from S3: bucket={}, key={}", props.getBucketName(), key);
        } catch (S3Exception e) {
            log.error("S3 delete failed: {}", e.awsErrorDetails().errorMessage(), e);
        }
    }

    /**
     * Keys under the direct-upload prefix that are older than {@code cutoff}. A browser can presign and
     * upload a file and then never send the comment, leaving the object behind with nothing referencing
     * it — this is how the cleanup job finds those.
     */
    public List<String> listDirectUploadsBefore(Instant cutoff) {
        if (!props.isEnabled()) {
            return List.of();
        }
        List<String> stale = new ArrayList<>();
        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(props.getBucketName())
                    .prefix(DIRECT_PREFIX + "/")
                    .build();
            for (ListObjectsV2Response page : s3Client.listObjectsV2Paginator(request)) {
                for (S3Object object : page.contents()) {
                    if (object.lastModified() != null && object.lastModified().isBefore(cutoff)) {
                        stale.add(object.key());
                    }
                }
            }
        } catch (S3Exception e) {
            log.error("S3 list failed: {}", e.awsErrorDetails().errorMessage(), e);
        }
        return stale;
    }

    /** Browser-usable URL for a stored key (bucket / endpoint must serve public reads). */
    public String publicUrl(String key) {
        // No usable URL when storage is off or unconfigured — return null rather than a malformed link.
        if (key == null || !props.isEnabled() || !StringUtils.hasText(props.getBucketName())) {
            return null;
        }
        if (StringUtils.hasText(props.getEndpoint())) {
            // MinIO / DigitalOcean Spaces / other S3-compatible endpoint. Path-style (endpoint/bucket/key),
            // matching how forcePathStyle uploads the object — same as the romchi config.
            return props.getEndpoint() + "/" + props.getBucketName() + "/" + key;
        }
        return String.format("https://%s.s3.%s.amazonaws.com/%s",
                props.getBucketName(), props.getRegion(), key);
    }

    private void requireEnabled() {
        if (!props.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "File storage is not configured");
        }
    }

    private String uniqueName(String originalFilename) {
        String clean = StringUtils.cleanPath(originalFilename != null ? originalFilename : "unnamed");
        if (clean.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid filename");
        }
        String extension = "";
        int dot = clean.lastIndexOf('.');
        if (dot > 0 && dot < clean.length() - 1) {
            extension = "." + clean.substring(dot + 1).toLowerCase();
        }
        return System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;
    }
}
