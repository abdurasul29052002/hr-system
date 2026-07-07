package uz.sonic.hr.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Multi-tenant happy path against real PostgreSQL:
 * register a user → create a team (becomes LEADER) → create a task in it → list it back.
 */
class TeamTaskFlowIT extends AbstractPostgresIT {

    private String registerAndToken(String username) throws Exception {
        String body = """
                {"fullName":"Flow User","username":"%s","password":"password123","language":"EN"}
                """.formatted(username);
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
                .andExpect(jsonPath("$.teamId").isNumber())
                .andExpect(jsonPath("$.role").value("LEADER"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("teamId").asLong();
    }

    @Test
    void register_createTeam_createTask_listTask() throws Exception {
        String token = registerAndToken("flow_leader");
        long teamId = createTeam(token, "Flow Team");

        // Create an (unassigned) task in the team → OPEN
        String created = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Team-Id", teamId)
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"First task\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("First task"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        long taskId = objectMapper.readTree(created).get("id").asLong();

        // List tasks for the team → contains the created task
        String list = mockMvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Team-Id", teamId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode tasks = objectMapper.readTree(list);
        assertThatContainsTask(tasks, taskId);
    }

    private void assertThatContainsTask(JsonNode tasks, long taskId) {
        boolean found = false;
        for (JsonNode t : tasks) {
            if (t.get("id").asLong() == taskId) {
                found = true;
                break;
            }
        }
        org.assertj.core.api.Assertions.assertThat(found)
                .as("created task %d should appear in the team task list", taskId)
                .isTrue();
    }

    @Test
    void creatingTaskWithoutTeamHeaderAndMultipleContexts_isRejected() throws Exception {
        // Registration auto-creates a personal team, so create a second one: with two teams and no
        // X-Team-Id header the request is ambiguous and must be rejected.
        String token = registerAndToken("flow_noteam");
        createTeam(token, "Second Team");
        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"orphan\"}"))
                .andExpect(status().is4xxClientError());
    }
}
