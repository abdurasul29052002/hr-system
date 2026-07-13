package uz.sonic.hr.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Entering tasks with lifecycle timestamps (to record past work) and members adding tasks that need a
 * leader's approval. Pins: the status follows the furthest time supplied, createdAt is clamped down to the
 * earliest supplied time, an out-of-order/no-start request is rejected, and a member's proposal is PENDING
 * until confirmation, then promoted to the status its recorded times imply.
 */
class TaskLifecycleEntryIT extends AbstractPostgresIT {

    @Test
    void leaderCreatesHistoricalDoneTask() throws Exception {
        String token = registerAndToken("life_leader");
        long teamId = createTeam(token, "Life Team");

        create(token, teamId, """
                {"title":"Old done task","takenAt":"2026-07-01T09:00:00Z",
                 "submittedAt":"2026-07-02T09:00:00Z","completedAt":"2026-07-03T09:00:00Z"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.takenAt").value("2026-07-01T09:00:00Z"))
                .andExpect(jsonPath("$.completedAt").value("2026-07-03T09:00:00Z"));
    }

    @Test
    void startedTimeAloneMakesItInProgress_andClampsCreatedAt() throws Exception {
        String token = registerAndToken("life_leader2");
        long teamId = createTeam(token, "Life Team 2");

        // createdAt is AFTER takenAt on purpose — the backend must clamp createdAt down, not reject.
        create(token, teamId, """
                {"title":"Started last week","createdAt":"2026-07-10T09:00:00Z","takenAt":"2026-07-01T09:00:00Z"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.createdAt").value("2026-07-01T09:00:00Z"));
    }

    @Test
    void noTimesKeepsItOpen() throws Exception {
        String token = registerAndToken("life_leader3");
        long teamId = createTeam(token, "Life Team 3");

        create(token, teamId, "{\"title\":\"Fresh task\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void finishedWithoutStart_isRejected() throws Exception {
        String token = registerAndToken("life_leader4");
        long teamId = createTeam(token, "Life Team 4");

        create(token, teamId, "{\"title\":\"Bad\",\"completedAt\":\"2026-07-03T09:00:00Z\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void memberProposalWithTimes_isPendingThenPromotedOnApproval() throws Exception {
        String leaderToken = registerAndToken("life_owner");
        long teamId = createTeam(leaderToken, "Proposal Team");

        // Leader adds a plain member.
        mockMvc.perform(post("/api/employees")
                        .header("Authorization", "Bearer " + leaderToken).header("X-Team-Id", teamId)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"fullName":"Member","username":"life_member","password":"password123","role":"MEMBER"}"""))
                .andExpect(status().isOk());
        String memberToken = login("life_member", "password123");

        // Member proposes a task they already started — it must be PENDING (awaiting approval), not IN_PROGRESS.
        String proposal = mockMvc.perform(post("/api/tasks/propose")
                        .header("Authorization", "Bearer " + memberToken).header("X-Team-Id", teamId)
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"My work\",\"takenAt\":\"2026-07-01T09:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long taskId = objectMapper.readTree(proposal).get("id").asLong();

        // Leader confirms → promoted to the status its recorded time implies (IN_PROGRESS).
        mockMvc.perform(post("/api/tasks/" + taskId + "/approve-proposal")
                        .header("Authorization", "Bearer " + leaderToken).header("X-Team-Id", teamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.takenAt").value("2026-07-01T09:00:00Z"));
    }

    // ---------------------------------------------------------------- helpers

    private org.springframework.test.web.servlet.ResultActions create(String token, long teamId, String body) throws Exception {
        return mockMvc.perform(post("/api/tasks")
                .header("Authorization", "Bearer " + token).header("X-Team-Id", teamId)
                .contentType(APPLICATION_JSON).content(body));
    }

    private String registerAndToken(String username) throws Exception {
        String body = """
                {"fullName":"%s","username":"%s","password":"password123","language":"EN"}
                """.formatted(username, username);
        String response = mockMvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private long createTeam(String token, String name) throws Exception {
        String response = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + token).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("teamId").asLong();
    }

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }
}
