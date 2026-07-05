package uz.sonic.hr.common.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import uz.sonic.hr.employee.Employee;
import uz.sonic.hr.team.TeamMembership;
import uz.sonic.hr.team.TeamMembershipRepository;

import java.util.List;

/**
 * Resolves the team context of the current HTTP request. The client selects a team
 * with the X-Team-Id header; when absent and the user belongs to exactly one team,
 * that team is used.
 */
@Component
@RequiredArgsConstructor
public class CurrentMembership {

    public static final String TEAM_HEADER = "X-Team-Id";

    private final CurrentUser currentUser;
    private final TeamMembershipRepository membershipRepository;
    private final HttpServletRequest request;

    public TeamMembership get() {
        Employee employee = currentUser.get();
        String header = request.getHeader(TEAM_HEADER);
        if (header != null && !header.isBlank()) {
            long teamId;
            try {
                teamId = Long.parseLong(header.trim());
            } catch (NumberFormatException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + TEAM_HEADER + " header");
            }
            return membershipRepository.findByEmployeeIdAndTeamId(employee.getId(), teamId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "You are not a member of this team"));
        }
        List<TeamMembership> memberships = membershipRepository.findAllByEmployeeIdOrderByJoinedAtAsc(employee.getId());
        if (memberships.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Create or join a team first");
        }
        if (memberships.size() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    TEAM_HEADER + " header is required when you belong to several teams");
        }
        return memberships.getFirst();
    }
}
