package uz.sonic.hr.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;
import uz.sonic.hr.common.dto.Dtos.TeamJoinRequestDto;
import uz.sonic.hr.common.enums.JoinRequestStatus;
import uz.sonic.hr.employee.Employee;
import uz.sonic.hr.employee.EmployeeRepository;
import uz.sonic.hr.team.TeamJoinRequestService;
import uz.sonic.hr.team.TeamMembershipRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Telegram bot approves/rejects join requests via {@code approveByEmployee}/{@code rejectByEmployee},
 * which identify the actor by {@link Employee} (a callback has no X-Team-Id header). This pins down that
 * authorization still runs against the request's <em>own</em> team: a manager of that team can decide,
 * an outsider cannot, and an already-decided request cannot be decided twice.
 */
class BotJoinRequestFlowIT extends AbstractPostgresIT {

    @Autowired
    private TeamJoinRequestService joinRequestService;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private TeamMembershipRepository membershipRepository;

    @Test
    void managerApprovesFromBot_requesterBecomesMember() throws Exception {
        JsonNode leader = register("bot_leader");
        long teamId = createTeam(leader.get("token").asText(), "Bot Team");
        JsonNode joiner = register("bot_joiner");
        long requestId = sendJoinRequest(joiner.get("token").asText(), teamId);

        Employee leaderEmployee = employeeRepository.findById(leader.get("employee").get("id").asLong()).orElseThrow();
        long joinerId = joiner.get("employee").get("id").asLong();

        TeamJoinRequestDto dto = joinRequestService.approveByEmployee(requestId, leaderEmployee);

        assertThat(dto.status()).isEqualTo(JoinRequestStatus.APPROVED);
        assertThat(membershipRepository.existsByEmployeeIdAndTeamId(joinerId, teamId)).isTrue();
    }

    @Test
    void outsiderCannotApproveFromBot() throws Exception {
        JsonNode leader = register("bot_leader2");
        long teamId = createTeam(leader.get("token").asText(), "Bot Team 2");
        long requestId = sendJoinRequest(register("bot_joiner2").get("token").asText(), teamId);

        // A team-less stranger who is neither a member nor a manager of the team must not decide.
        Employee stranger = employeeRepository.findById(register("bot_outsider").get("employee").get("id").asLong())
                .orElseThrow();

        assertThatThrownBy(() -> joinRequestService.approveByEmployee(requestId, stranger))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void alreadyDecidedRequestCannotBeDecidedAgain() throws Exception {
        JsonNode leader = register("bot_leader3");
        long teamId = createTeam(leader.get("token").asText(), "Bot Team 3");
        long requestId = sendJoinRequest(register("bot_joiner3").get("token").asText(), teamId);
        Employee leaderEmployee = employeeRepository.findById(leader.get("employee").get("id").asLong()).orElseThrow();

        joinRequestService.approveByEmployee(requestId, leaderEmployee);

        assertThatThrownBy(() -> joinRequestService.rejectByEmployee(requestId, leaderEmployee))
                .isInstanceOf(ResponseStatusException.class);
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

    private long sendJoinRequest(String token, long teamId) throws Exception {
        String response = mockMvc.perform(post("/api/team-join-requests/" + teamId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }
}
