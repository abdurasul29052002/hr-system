package uz.sonic.hr.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for integration tests: boots the full Spring context against a real PostgreSQL started by
 * Testcontainers ({@code @ServiceConnection} wires the datasource), with MockMvc going through the
 * whole MVC + Security filter chain. The container is static so it is shared and the context cached
 * across all IT classes. Requires a Docker daemon — run via {@code mvn verify} (failsafe), not the
 * normal build. Class names end in {@code IT} so surefire ignores them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class AbstractPostgresIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // A valid (non-default) JWT secret and no S3 — this profile does not run ProdSecretGuard.
        registry.add("app.jwt.secret", () -> "integration-test-jwt-secret-key-with-min-32-characters");
        registry.add("app.s3.enabled", () -> "false");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;
}
