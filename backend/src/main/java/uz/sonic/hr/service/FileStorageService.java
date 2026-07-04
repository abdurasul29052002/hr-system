package uz.sonic.hr.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    private final Path uploadBasePath;
    private final long maxFileSize;

    public FileStorageService(
            @Value("${app.upload.base-path:./data/uploads}") String basePath,
            @Value("${app.upload.max-file-size:5242880}") long maxFileSize) { // 5MB default
        this.uploadBasePath = Paths.get(basePath).toAbsolutePath().normalize();
        this.maxFileSize = maxFileSize;

        try {
            Files.createDirectories(this.uploadBasePath);
            log.info("Upload directory created/verified at: {}", this.uploadBasePath);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory!", e);
        }
    }

    /**
     * Store a file for a task. Files are organized by task ID.
     *
     * @param file   The uploaded file
     * @param taskId The task ID
     * @return Relative file path (from base upload path)
     */
    public String storeFile(MultipartFile file, Long taskId) {
        // Validate file
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot upload empty file");
        }

        if (file.getSize() > maxFileSize) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File size exceeds maximum allowed size: " + (maxFileSize / 1024 / 1024) + "MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !isAllowedImageType(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only image files are allowed (jpg, png, gif, webp)");
        }

        // Sanitize filename
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null
                ? file.getOriginalFilename()
                : "unnamed");

        if (originalFilename.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid filename");
        }

        // Generate unique filename
        String extension = getFileExtension(originalFilename);
        String uniqueFilename = System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8)
                + (extension.isEmpty() ? "" : "." + extension);

        // Create task directory
        Path taskDir = uploadBasePath.resolve("tasks").resolve(String.valueOf(taskId));
        try {
            Files.createDirectories(taskDir);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not create directory for task");
        }

        // Store file
        Path targetLocation = taskDir.resolve(uniqueFilename);
        try {
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            log.info("File stored: {}", targetLocation);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not store file", e);
        }

        // Return relative path
        return "tasks/" + taskId + "/" + uniqueFilename;
    }

    /**
     * Delete a file by its relative path.
     */
    public void deleteFile(String relativePath) {
        try {
            Path filePath = uploadBasePath.resolve(relativePath).normalize();

            // Security check: ensure file is within upload directory
            if (!filePath.startsWith(uploadBasePath)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path");
            }

            Files.deleteIfExists(filePath);
            log.info("File deleted: {}", filePath);
        } catch (IOException e) {
            log.error("Failed to delete file: {}", relativePath, e);
        }
    }

    /**
     * Load a file as a Resource.
     */
    public Resource loadFileAsResource(String relativePath) {
        try {
            Path filePath = uploadBasePath.resolve(relativePath).normalize();

            // Security check
            if (!filePath.startsWith(uploadBasePath)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path");
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found", e);
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
