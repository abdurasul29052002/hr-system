package uz.sonic.hr.team;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uz.sonic.hr.employee.Employee;
import uz.sonic.hr.common.enums.Role;
import uz.sonic.hr.team.Team;
import uz.sonic.hr.team.TeamMembership;
import uz.sonic.hr.team.TeamMembershipRepository;
import uz.sonic.hr.team.TeamRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMembershipRepository membershipRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /** Creates a team; the creator becomes its LEADER. A user may create/join any number of teams. */
    @Transactional
    public TeamMembership create(String name, Employee creator) {
        Team team = teamRepository.save(Team.builder().name(name.trim()).build());
        return membershipRepository.save(TeamMembership.builder()
                .employee(creator)
                .team(team)
                .role(Role.LEADER)
                .build());
    }

    @Transactional(readOnly = true)
    public List<TeamMembership> membershipsOf(Employee employee) {
        return membershipRepository.findAllByEmployeeIdOrderByJoinedAtAsc(employee.getId());
    }

    /**
     * Permanently deletes a team and EVERYTHING that belongs to it — tasks (with their comments,
     * mentions, comment attachments and task attachments), tags, invites, member labels and all
     * memberships. Children are removed before parents because the schema uses plain (RESTRICT) FKs.
     * NOTE: attachment BLOBs on S3 are not removed here (a harmless orphan; cleaned up separately).
     */
    @Transactional
    public void deleteTeam(Long teamId) {
        if (!teamRepository.existsById(teamId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found");
        }
        exec("DELETE FROM comment_attachments WHERE comment_id IN (SELECT c.id FROM task_comments c JOIN tasks t ON c.task_id = t.id WHERE t.team_id = :tid)", teamId);
        exec("DELETE FROM comment_mentions WHERE comment_id IN (SELECT c.id FROM task_comments c JOIN tasks t ON c.task_id = t.id WHERE t.team_id = :tid)", teamId);
        exec("DELETE FROM notifications WHERE related_comment_id IN (SELECT c.id FROM task_comments c JOIN tasks t ON c.task_id = t.id WHERE t.team_id = :tid) OR related_task_id IN (SELECT id FROM tasks WHERE team_id = :tid) OR related_invite_id IN (SELECT id FROM team_invites WHERE team_id = :tid)", teamId);
        exec("DELETE FROM task_comments WHERE task_id IN (SELECT id FROM tasks WHERE team_id = :tid)", teamId);
        exec("DELETE FROM task_attachments WHERE task_id IN (SELECT id FROM tasks WHERE team_id = :tid)", teamId);
        exec("DELETE FROM task_tags WHERE task_id IN (SELECT id FROM tasks WHERE team_id = :tid)", teamId);
        exec("DELETE FROM tasks WHERE team_id = :tid", teamId);
        exec("DELETE FROM tags WHERE team_id = :tid", teamId);
        exec("DELETE FROM team_invites WHERE team_id = :tid", teamId);
        exec("DELETE FROM membership_labels WHERE membership_id IN (SELECT id FROM team_memberships WHERE team_id = :tid) OR label_id IN (SELECT id FROM member_labels WHERE team_id = :tid)", teamId);
        exec("DELETE FROM member_labels WHERE team_id = :tid", teamId);
        exec("DELETE FROM team_memberships WHERE team_id = :tid", teamId);
        exec("DELETE FROM teams WHERE id = :tid", teamId);
    }

    private void exec(String sql, Long teamId) {
        entityManager.createNativeQuery(sql).setParameter("tid", teamId).executeUpdate();
    }

    public static void requireLeader(TeamMembership membership) {
        if (membership.getRole() != Role.LEADER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a team leader can do this");
        }
    }

    /** LEADER or MANAGER. */
    public static void requireManager(TeamMembership membership) {
        if (!isManagerOrLeader(membership)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a leader or manager can do this");
        }
    }

    public static boolean isManagerOrLeader(TeamMembership membership) {
        return membership.getRole() == Role.LEADER || membership.getRole() == Role.MANAGER;
    }

    /**
     * Get and validate team membership for the given employee and team.
     * If teamId is null, returns the first membership found.
     * Throws 404 if no membership exists.
     */
    @Transactional(readOnly = true)
    public TeamMembership requireMembership(Long employeeId, Long teamId) {
        if (teamId == null) {
            // Return first membership
            List<TeamMembership> memberships = membershipRepository.findAllByEmployeeIdOrderByJoinedAtAsc(employeeId);
            if (memberships.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "You are not a member of any team");
            }
            return memberships.get(0);
        }

        return membershipRepository.findByEmployeeIdAndTeamId(employeeId, teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this team"));
    }
}
