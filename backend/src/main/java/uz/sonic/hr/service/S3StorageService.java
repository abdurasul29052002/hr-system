package uz.sonic.hr.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import uz.sonic.hr.config.S3Properties;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3StorageService {

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    /**
     * Upload file to S3.
     *
     * @param file   The file to upload
     * @param folder Folder path in S3 (e.g., "tasks/123")
     * @return S3 object key
     */
    public String uploadFile(MultipartFile file, String folder) {
        if (s3Client == null || !s3Properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "S3 storage is not configured");
        }

        // Validate file
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot upload empty file");
        }

        // Validate file type (images only)
        String contentType = file.getContentType();
        if (contentType == null || !isAllowedImageType(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only image files are allowed (jpg, png, gif, webp)");
        }

        // Generate unique key
        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed"
        );

        if (originalFilename.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid filename");
        }

        String extension = getFileExtension(originalFilename);
        String filename = System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8)
                + (extension.isEmpty() ? "" : "." + extension);

        String key = folder + "/" + filename;

        try {
            // Upload to S3
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(s3Properties.getBucketName())
                    .key(key)
                    .contentType(contentType)
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            log.info("File uploaded to S3: bucket={}, key={}", s3Properties.getBucketName(), key);
            return key;

        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to upload file", e);
        } catch (S3Exception e) {
            log.error("S3 upload failed: {}", e.awsErrorDetails().errorMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "S3 upload failed", e);
        }
    }

    /**
     * Delete file from S3.
     */
    public void deleteFile(String key) {
        if (s3Client == null || !s3Properties.isEnabled()) {
            log.warn("S3 storage is not configured, skipping delete");
            return;
        }

        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(s3Properties.getBucketName())
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteRequest);
            log.info("File deleted from S3: bucket={}, key={}", s3Properties.getBucketName(), key);

        } catch (S3Exception e) {
            log.error("S3 delete failed: {}", e.awsErrorDetails().errorMessage(), e);
        }
    }

    /**
     * Generate presigned URL for downloading file (valid for 1 hour).
     */
    public String getPresignedUrl(String key) {
        if (s3Client == null || !s3Properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "S3 storage is not configured");
        }

        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(s3Properties.getBucketName())
                    .key(key)
                    .build();

            // Generate presigned URL (valid for 1 hour)
            URL url = s3Client.utilities()
                    .getUrl(builder -> builder.bucket(s3Properties.getBucketName()).key(key));

            return url.toString();

        } catch (S3Exception e) {
            log.error("Failed to generate presigned URL: {}", e.awsErrorDetails().errorMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate download URL", e);
        }
    }

    /**
     * Get public URL for a file (if bucket is public).
     */
    public String getPublicUrl(String key) {
        if (s3Properties.getEndpoint() != null) {
            // Custom endpoint (MinIO, DigitalOcean Spaces)
            return s3Properties.getEndpoint() + "/" + s3Properties.getBucketName() + "/" + key;
        } else {
            // AWS S3
            return String.format("https://%s.s3.%s.amazonaws.com/%s",
                    s3Properties.getBucketName(),
                    s3Properties.getRegion(),
                    key);
        }
    }

    private boolean isAllowedImageType(String contentType) {
        return contentType.equals("image/jpeg")
                || contentType.equals("image/png")
                || contentType.equals("image/gif")
                || contentType.equals("image/webp");
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }
}
