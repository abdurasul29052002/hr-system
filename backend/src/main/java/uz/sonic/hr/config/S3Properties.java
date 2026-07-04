package uz.sonic.hr.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.s3")
@Data
public class S3Properties {
    private boolean enabled = true;
    private String region = "us-east-1";
    private String bucketName;
    private String accessKey;
    private String secretKey;
    private String endpoint; // For MinIO or custom S3-compatible storage
    private boolean pathStyleAccess = false; // MinIO requires true
}
