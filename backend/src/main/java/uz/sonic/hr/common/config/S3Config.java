package uz.sonic.hr.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
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

    /**
     * Builds the S3 client used by {@link uz.sonic.hr.common.storage.StorageService}.
     * When explicit access/secret keys are absent the AWS default credentials chain is
     * used (IAM roles, env vars, etc.). Credentials are resolved lazily, so the bean is
     * created even before storage is configured — uploads then fail with a clear error.
     */
    @Bean
    public S3Client s3Client() {
        String region = StringUtils.hasText(s3Properties.getRegion()) ? s3Properties.getRegion() : "us-east-1";
        S3ClientBuilder builder = S3Client.builder().region(Region.of(region));

        if (StringUtils.hasText(s3Properties.getAccessKey()) && StringUtils.hasText(s3Properties.getSecretKey())) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(s3Properties.getAccessKey(), s3Properties.getSecretKey())));
        }

        // Custom endpoint (MinIO, DigitalOcean Spaces, etc.). forcePathStyle(true) — exactly like the
        // proven romchi config — puts the bucket in the PATH, not the host, so the SDK never prepends
        // (and doubles) the bucket onto the endpoint host and the wildcard TLS cert always matches. The
        // endpoint is used as-is (DO's per-Space origin, bucket included, is fine).
        if (StringUtils.hasText(s3Properties.getEndpoint())) {
            builder.endpointOverride(URI.create(s3Properties.getEndpoint()))
                    .forcePathStyle(true);
        }

        log.info("S3 client initialized: enabled={}, bucket={}, region={}, endpoint={}",
                s3Properties.isEnabled(),
                s3Properties.getBucketName(),
                region,
                StringUtils.hasText(s3Properties.getEndpoint()) ? s3Properties.getEndpoint() : "AWS");

        return builder.build();
    }
}
