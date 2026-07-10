package uz.sonic.hr.common.storage;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import uz.sonic.hr.common.config.S3Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Pure unit test of {@link StorageService#publicUrl} — no S3 calls are made. */
class StorageServiceTest {

    private final S3Client s3 = mock(S3Client.class);

    private S3Properties props(boolean enabled, String bucket, String region, String endpoint) {
        S3Properties p = new S3Properties();
        p.setEnabled(enabled);
        p.setBucketName(bucket);
        p.setRegion(region);
        p.setEndpoint(endpoint);
        return p;
    }

    @Test
    void publicUrl_awsVirtualHostStyle() {
        StorageService s = new StorageService(s3, props(true, "my-bucket", "eu-central-1", null));

        assertThat(s.publicUrl("tasks/1/a.png"))
                .isEqualTo("https://my-bucket.s3.eu-central-1.amazonaws.com/tasks/1/a.png");
    }

    @Test
    void publicUrl_customEndpointPathStyle() {
        StorageService s = new StorageService(s3,
                props(true, "my-bucket", "us-east-1", "https://fra1.digitaloceanspaces.com"));

        assertThat(s.publicUrl("comments/2/b.png"))
                .isEqualTo("https://fra1.digitaloceanspaces.com/my-bucket/comments/2/b.png");
    }

    @Test
    void publicUrl_bucketInEndpointPathStyle() {
        // DO's per-Space origin includes the bucket (e.g. romchi-files.ams3...). With forcePathStyle the
        // client keeps that host (cert matches) and puts the bucket in the path, so the public URL is
        // simply endpoint/bucket/key — the same location the object was uploaded to. Like the romchi config.
        StorageService s = new StorageService(s3,
                props(true, "my-bucket", "us-east-1", "https://my-bucket.ams3.digitaloceanspaces.com"));

        assertThat(s.publicUrl("comments/2/b.png"))
                .isEqualTo("https://my-bucket.ams3.digitaloceanspaces.com/my-bucket/comments/2/b.png");
    }

    @Test
    void publicUrl_nullWhenDisabled() {
        StorageService s = new StorageService(s3, props(false, "my-bucket", "us-east-1", null));

        assertThat(s.publicUrl("tasks/1/a.png")).isNull();
    }

    @Test
    void publicUrl_nullWhenBucketBlank() {
        StorageService s = new StorageService(s3, props(true, "", "us-east-1", null));

        assertThat(s.publicUrl("tasks/1/a.png")).isNull();
    }

    @Test
    void publicUrl_nullForNullKey() {
        StorageService s = new StorageService(s3, props(true, "my-bucket", "us-east-1", null));

        assertThat(s.publicUrl(null)).isNull();
    }
}
