package uz.sonic.hr.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

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

    /**
     * The region endpoint with any accidental leading {@code <bucket>.} host label removed and trailing
     * slashes trimmed. DigitalOcean's panel shows a Space's origin as
     * {@code https://<space>.<region>.digitaloceanspaces.com}; pasting that as the endpoint makes the
     * SDK prepend the bucket AGAIN (virtual-hosted style) → {@code <space>.<space>.<region>...}, which
     * the wildcard TLS cert ({@code *.<region>.digitaloceanspaces.com}, one label only) rejects. We use
     * path-style addressing against this region endpoint instead, so uploads and the public URLs both
     * resolve to {@code https://<region>.../<bucket>/<key>}.
     */
    public String effectiveEndpoint() {
        if (endpoint == null || endpoint.isBlank()) {
            return endpoint;
        }
        String trimmed = endpoint.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (bucketName == null || bucketName.isBlank()) {
            return trimmed;
        }
        try {
            URI uri = URI.create(trimmed);
            String host = uri.getHost();
            String prefix = bucketName + ".";
            if (host != null && host.startsWith(prefix)) {
                String scheme = uri.getScheme() != null ? uri.getScheme() : "https";
                String port = uri.getPort() > -1 ? ":" + uri.getPort() : "";
                return scheme + "://" + host.substring(prefix.length()) + port;
            }
        } catch (RuntimeException ignored) {
            // Malformed endpoint — fall back to the trimmed value as given.
        }
        return trimmed;
    }
}
