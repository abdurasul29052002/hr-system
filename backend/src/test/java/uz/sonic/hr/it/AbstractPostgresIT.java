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
        // application.yml reads these env placeholders with no defaults; supply dummies so the context
        // boots without a real .env. @ServiceConnection still overrides the DB connection to the container.
        registry.add("APP_JWT_SECRET", () -> "integration-test-jwt-secret-key-with-min-32-characters");
        registry.add("APP_ADMIN_USERNAME", () -> "admin");
        registry.add("APP_ADMIN_PASSWORD", () -> "integration-test-admin-password");
        registry.add("CORS_ALLOWED_ORIGINS", () -> "http://localhost:3000");
        registry.add("DB_HOST", () -> "localhost");
        registry.add("DB_PORT", () -> "5432");
        registry.add("DB_NAME", () -> "test");
        registry.add("DB_USER", () -> "test");
        registry.add("DB_PASSWORD", () -> "test");
        registry.add("S3_ENABLED", () -> "false");
        registry.add("S3_REGION", () -> "us-east-1");
        registry.add("S3_BUCKET_NAME", () -> "test-bucket");
        registry.add("S3_ACCESS_KEY", () -> "test");
        registry.add("S3_SECRET_KEY", () -> "test");
        registry.add("S3_ENDPOINT", () -> "");
        // Keep the Telegram bot off in tests even if a local backend/.env is imported.
        registry.add("app.bot.token", () -> "");
        registry.add("app.bot.username", () -> "");
        // Real flow: Liquibase creates the schema on the fresh container, Hibernate then validates it.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;
}
