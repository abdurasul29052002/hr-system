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
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import uz.sonic.hr.common.config.S3Properties;

import java.io.IOException;
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

    private final S3Client s3Client;
    private final S3Properties props;

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
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only image files are allowed (jpg, png, gif, webp)");
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
