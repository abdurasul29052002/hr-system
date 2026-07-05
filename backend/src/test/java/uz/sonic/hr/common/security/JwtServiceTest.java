package uz.sonic.hr.common.security;

import org.junit.jupiter.api.Test;
import uz.sonic.hr.common.security.JwtService.AuthToken;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit test — no Spring context, no DB. */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-at-least-32-characters-long-xxxx";
    private final JwtService jwtService = new JwtService(SECRET, 72);

    @Test
    void generateAndValidate_roundTrips() {
        AuthToken parsed = jwtService.validate(jwtService.generateToken(42L, false, "jasur"));

        assertThat(parsed).isNotNull();
        assertThat(parsed.id()).isEqualTo(42L);
        assertThat(parsed.admin()).isFalse();
    }

    @Test
    void generate_carriesAdminClaim() {
        AuthToken parsed = jwtService.validate(jwtService.generateToken(1L, true, "admin"));

        assertThat(parsed).isNotNull();
        assertThat(parsed.admin()).isTrue();
    }

    @Test
    void validate_rejectsGarbage() {
        assertThat(jwtService.validate("not-a-jwt")).isNull();
        assertThat(jwtService.validate("")).isNull();
    }

    @Test
    void validate_rejectsTokenSignedWithAnotherKey() {
        JwtService attacker = new JwtService("a-totally-different-secret-key-32-chars-min", 72);

        assertThat(jwtService.validate(attacker.generateToken(7L, true, "attacker"))).isNull();
    }

    @Test
    void validate_rejectsExpiredToken() {
        // -1 hour expiry → the token is already expired the moment it is issued.
        JwtService expiring = new JwtService(SECRET, -1);

        assertThat(jwtService.validate(expiring.generateToken(1L, false, "x"))).isNull();
    }
}
