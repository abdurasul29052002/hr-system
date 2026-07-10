package uz.sonic.hr.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.s3")
@Data
public class S3Properties {
    private boolean enabled;
    private String region;
    private String bucketName;
    private String accessKey;
    private String secretKey;
    private String endpoint;
}
