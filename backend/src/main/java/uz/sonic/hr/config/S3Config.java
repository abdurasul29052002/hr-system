package uz.sonic.hr.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class S3Config {

    private final S3Properties s3Properties;

    @Bean
    public S3Client s3Client() {
        if (!s3Properties.isEnabled()) {
            log.info("S3 storage is disabled");
            return null;
        }

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(s3Properties.getRegion()));

        // Credentials
        if (s3Properties.getAccessKey() != null && s3Properties.getSecretKey() != null) {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(
                    s3Properties.getAccessKey(),
                    s3Properties.getSecretKey()
            );
            builder.credentialsProvider(StaticCredentialsProvider.create(credentials));
        }

        // Custom endpoint (MinIO, DigitalOcean Spaces, etc.)
        if (s3Properties.getEndpoint() != null) {
            builder.endpointOverride(URI.create(s3Properties.getEndpoint()));
        }

        // Path-style access (required for MinIO)
        if (s3Properties.isPathStyleAccess()) {
            builder.forcePathStyle(true);
        }

        S3Client client = builder.build();
        log.info("S3 client initialized: bucket={}, region={}, endpoint={}",
                s3Properties.getBucketName(),
                s3Properties.getRegion(),
                s3Properties.getEndpoint() != null ? s3Properties.getEndpoint() : "AWS");

        return client;
    }
}
