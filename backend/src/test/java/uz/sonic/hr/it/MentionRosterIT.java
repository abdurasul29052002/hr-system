package uz.sonic.hr.it;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @mention autocomplete needs the team roster for EVERY member, not just managers. The full
 * {@code GET /api/employees} stays manager-only (it exposes phone + Telegram link codes), so a
 * lightweight {@code GET /api/employees/mentionable} serves the roster to any team member. This pins
 * both halves: a plain member can read the mention roster but is still forbidden the full one.
 */
class MentionRosterIT extends AbstractPostgresIT {

    @Test
    void anyMemberCanReadMentionRoster_butNotTheFullRoster() throws Exception {
        String leaderToken = registerAndToken("roster_leader");
        long teamId = createTeam(leaderToken, "Roster Team");

        // Leader adds a plain MEMBER to the team.
        mockMvc.perform(post("/api/employees")
                        .header("Authorization", "Bearer " + leaderToken)
                        .header("X-Team-Id", teamId)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"fullName":"Member One","username":"roster_member","password":"password123","role":"MEMBER"}
                                """))
                .andExpect(status().isOk());

        String memberToken = login("roster_member", "password123");

        // The member CAN read the lightweight mention roster — both people are in it.
        mockMvc.perform(get("/api/employees/mentionable")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("X-Team-Id", teamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].username", hasItems("roster_leader", "roster_member")));

        // But the full roster (phone / Telegram codes) stays manager-only.
        mockMvc.perform(get("/api/employees")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("X-Team-Id", teamId))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- helpers

    private String registerAndToken(String username) throws Exception {
        String body = """
                {"fullName":"%s","username":"%s","password":"password123","language":"EN"}
                """.formatted(username, username);
        String response = mockMvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private long createTeam(String token, String name) throws Exception {
        String response = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("teamId").asLong();
    }

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }
}
