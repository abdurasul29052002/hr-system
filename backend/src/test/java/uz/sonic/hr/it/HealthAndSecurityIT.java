package uz.sonic.hr.it;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies the deploy-critical security posture: health is public, the API is not. */
class HealthAndSecurityIT extends AbstractPostgresIT {

    @Test
    void actuatorHealthIsPublicAndUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    /**
     * Missing (or expired) credentials must be 401, not 403. The web client only tears down its session on
     * 401; when this answered 403 an expired token left users apparently signed in with every call failing.
     */
    @Test
    void protectedApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized());
    }

    /** A token that is present but not valid is still "not authenticated" — 401, never 403. */
    @Test
    void protectedApiWithInvalidTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/tasks").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }
}
