package uz.sonic.hr.common.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure unit test of the prod fail-fast guard. */
class ProdSecretGuardTest {

    @Test
    void rejectsCommittedDevDefault() {
        ProdSecretGuard guard = new ProdSecretGuard("change-me-hr-system-secret-key-0123456789");

        assertThatThrownBy(guard::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_JWT_SECRET");
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> new ProdSecretGuard("   ").verify())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsTooShort() {
        assertThatThrownBy(() -> new ProdSecretGuard("short-secret").verify())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsStrongSecret() {
        ProdSecretGuard guard = new ProdSecretGuard("a-strong-random-production-secret-key-32chars-minimum");

        assertThatCode(guard::verify).doesNotThrowAnyException();
    }
}
