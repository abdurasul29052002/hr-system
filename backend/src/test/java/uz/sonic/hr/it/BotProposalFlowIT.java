package uz.sonic.hr.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;
import uz.sonic.hr.common.dto.Dtos.TaskDto;
import uz.sonic.hr.common.enums.TaskStatus;
import uz.sonic.hr.employee.Employee;
import uz.sonic.hr.employee.EmployeeRepository;
import uz.sonic.hr.task.TaskRepository;
import uz.sonic.hr.task.TaskService;
import uz.sonic.hr.team.TeamJoinRequestService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A member proposes a task (PENDING); the team's leader confirms or declines it straight from Telegram via
 * {@code approveProposalByEmployee}/{@code rejectProposalByEmployee}, which identify the actor by
 * {@link Employee} (a callback has no X-Team-Id header). This pins down that authorization runs against the
 * proposal's <em>own</em> team: a leader of that team can decide, an outsider cannot, an approved proposal
 * becomes a real task, a declined one is deleted, and neither can be decided twice.
 */
class BotProposalFlowIT extends AbstractPostgresIT {

    @Autowired
    private TaskService taskService;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private TeamJoinRequestService joinRequestService;

    @Test
    void leaderApprovesProposalFromBot_becomesInProgressTask() throws Exception {
        Ctx c = setUpMemberInTeam("prop_leader_a", "prop_member_a", "Prop Team A");
        long taskId = proposeTask(c.memberToken, c.teamId, "Refactor the export job");

        TaskDto dto = taskService.approveProposalByEmployee(taskId, c.leader);

        assertThat(dto.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(taskRepository.findById(taskId)).isPresent();
    }

    @Test
    void leaderRejectsProposalFromBot_taskIsDeleted() throws Exception {
        Ctx c = setUpMemberInTeam("prop_leader_b", "prop_member_b", "Prop Team B");
        long taskId = proposeTask(c.memberToken, c.teamId, "Investigate flaky test");

        String proposerName = taskService.rejectProposalByEmployee(taskId, c.leader);

        assertThat(proposerName).isNotBlank();
        assertThat(taskRepository.findById(taskId)).isEmpty();
    }

    @Test
    void outsiderCannotDecideProposalFromBot() throws Exception {
        Ctx c = setUpMemberInTeam("prop_leader_c", "prop_member_c", "Prop Team C");
        long taskId = proposeTask(c.memberToken, c.teamId, "Write docs");

        // A team-less stranger who is neither a member nor a manager of the team must not decide.
        Employee stranger = employeeRepository
                .findById(register("prop_outsider_c").get("employee").get("id").asLong()).orElseThrow();

        assertThatThrownBy(() -> taskService.approveProposalByEmployee(taskId, stranger))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void plainMemberCannotDecideProposalFromBot() throws Exception {
        Ctx c = setUpMemberInTeam("prop_leader_d", "prop_member_d", "Prop Team D");
        long taskId = proposeTask(c.memberToken, c.teamId, "Clean up logs");

        // The proposer is only a MEMBER of the team, so they cannot confirm their own (or any) proposal.
        Employee member = employeeRepository.findById(c.memberId).orElseThrow();

        assertThatThrownBy(() -> taskService.approveProposalByEmployee(taskId, member))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void alreadyDecidedProposalCannotBeDecidedAgain() throws Exception {
        Ctx c = setUpMemberInTeam("prop_leader_e", "prop_member_e", "Prop Team E");
        long taskId = proposeTask(c.memberToken, c.teamId, "Upgrade dependency");

        taskService.approveProposalByEmployee(taskId, c.leader);

        assertThatThrownBy(() -> taskService.rejectProposalByEmployee(taskId, c.leader))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ---------------------------------------------------------------- helpers

    /** A leader with a team, plus a member who joined it (via the approved join-request flow). */
    private record Ctx(Employee leader, long teamId, String memberToken, long memberId) {
    }

    private Ctx setUpMemberInTeam(String leaderName, String memberName, String teamName) throws Exception {
        JsonNode leaderNode = register(leaderName);
        long teamId = createTeam(leaderNode.get("token").asText(), teamName);
        Employee leader = employeeRepository.findById(leaderNode.get("employee").get("id").asLong()).orElseThrow();

        JsonNode memberNode = register(memberName);
        long requestId = sendJoinRequest(memberNode.get("token").asText(), teamId);
        joinRequestService.approveByEmployee(requestId, leader); // member becomes a MEMBER of the team

        return new Ctx(leader, teamId, memberNode.get("token").asText(),
                memberNode.get("employee").get("id").asLong());
    }

    private long proposeTask(String token, long teamId, String title) throws Exception {
        String response = mockMvc.perform(post("/api/tasks/propose")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Team-Id", teamId)
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"priority\":\"MEDIUM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

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
