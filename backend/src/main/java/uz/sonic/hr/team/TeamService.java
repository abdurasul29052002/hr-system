package uz.sonic.hr.team;

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
