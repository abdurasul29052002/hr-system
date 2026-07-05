package uz.sonic.hr.common.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fail-fast guard for the {@code prod} profile: refuses to start if the JWT secret is missing, blank,
 * too short, or still the committed dev default. Without it a misconfigured prod deploy would silently
 * sign tokens with a public, source-controlled key — anyone could then forge an admin token.
 */
@Component
@Profile("prod")
@Slf4j
public class ProdSecretGuard {

    /** The dev fallback defined in application.yml — must never be used in production. */
    private static final String INSECURE_DEFAULT = "change-me-hr-system-secret-key-0123456789";
    private static final int MIN_LENGTH = 32;

    private final String jwtSecret;

    public ProdSecretGuard(@Value("${app.jwt.secret:}") String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    @PostConstruct
    void verify() {
        if (jwtSecret == null || jwtSecret.isBlank()
                || INSECURE_DEFAULT.equals(jwtSecret)
                || jwtSecret.length() < MIN_LENGTH) {
            throw new IllegalStateException(
                    "APP_JWT_SECRET must be set to a strong, non-default value (>= " + MIN_LENGTH
                            + " characters) when running with the 'prod' profile. "
                            + "Generate one with: openssl rand -base64 64");
        }
        log.info("JWT secret validated for prod profile.");
    }
}
