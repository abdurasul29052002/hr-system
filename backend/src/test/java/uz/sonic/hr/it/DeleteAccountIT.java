package uz.sonic.hr.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.sonic.hr.employee.Employee;
import uz.sonic.hr.employee.EmployeeRepository;
import uz.sonic.hr.team.TeamRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Self-service account deletion (deactivate + anonymize). Pins: a solo owner's account is
 * anonymized and their solo team removed (and the username is freed); the last leader of a SHARED
 * team is blocked; and a wrong password is rejected.
 */
class DeleteAccountIT extends AbstractPostgresIT {

    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private TeamRepository teamRepository;

    @Test
    void soloOwnerDeletes_accountAnonymized_teamGone_usernameFreed() throws Exception {
        JsonNode reg = register("del_solo");
        String token = reg.get("token").asText();
        long employeeId = reg.get("employee").get("id").asLong();
        long teamId = createTeam(token, "Solo Team");

        deleteAccount(token, "password123").andExpect(status().isNoContent());

        // Row kept but anonymized + disabled.
        Employee gone = employeeRepository.findById(employeeId).orElseThrow();
        assertThat(gone.isActive()).isFalse();
        assertThat(gone.getFullName()).isEqualTo("Deleted user");
        assertThat(gone.getUsername()).isEqualTo("deleted_" + employeeId);
        // Solo team fully removed.
        assertThat(teamRepository.existsById(teamId)).isFalse();
        // Login with the old credentials no longer works.
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"del_solo\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized());
        // The freed username can be registered again.
        mockMvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON)
                        .content("""
                                {"fullName":"New Solo","username":"del_solo","password":"password123","language":"EN"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void lastLeaderOfSharedTeam_isBlocked() throws Exception {
        JsonNode reg = register("del_leader");
        String token = reg.get("token").asText();
        long teamId = createTeam(token, "Shared Team");

        // Add a second member so the team is shared and the leader is its only leader.
        mockMvc.perform(post("/api/employees")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Team-Id", teamId)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"fullName":"Member","username":"del_member","password":"password123","role":"MEMBER"}
                                """))
                .andExpect(status().isOk());

        deleteAccount(token, "password123").andExpect(status().isConflict());

        // The account is untouched — it can still log in.
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"del_leader\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPassword_isRejected() throws Exception {
        String token = register("del_wrong").get("token").asText();
        deleteAccount(token, "not-my-password").andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- helpers

    private JsonNode register(String username) throws Exception {
        String body = """
                {"fullName":"%s","username":"%s","password":"password123","language":"EN"}
                """.formatted(username, username);
        String response = mockMvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
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

    private org.springframework.test.web.servlet.ResultActions deleteAccount(String token, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/delete-account")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"password\":\"" + password + "\"}"));
    }
}
