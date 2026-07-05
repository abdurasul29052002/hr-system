package uz.sonic.hr.it;

import org.junit.jupiter.api.Test;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end auth flow against a real PostgreSQL: register → token → /me → login. */
class AuthIT extends AbstractPostgresIT {

    private String register(String username) throws Exception {
        String body = """
                {"fullName":"Test User","username":"%s","password":"password123","language":"EN"}
                """.formatted(username);
        String response = mockMvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.employee.username").value(username))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    @Test
    void register_thenAuthenticatedMe() throws Exception {
        String token = register("authit_user1");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("authit_user1"));
    }

    @Test
    void login_withCorrectCredentials_returnsToken() throws Exception {
        register("authit_user2");

        String login = """
                {"username":"authit_user2","password":"password123"}
                """;
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void login_withWrongPassword_isUnauthorized() throws Exception {
        register("authit_user3");

        String login = """
                {"username":"authit_user3","password":"wrong-password"}
                """;
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content(login))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withoutToken_isUnauthorizedOrForbidden() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().is4xxClientError());
    }
}
