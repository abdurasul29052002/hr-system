package uz.sonic.hr.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uz.sonic.hr.entity.Employee;
import uz.sonic.hr.entity.Role;
import uz.sonic.hr.entity.Team;
import uz.sonic.hr.entity.TeamMembership;
import uz.sonic.hr.repo.TeamMembershipRepository;
import uz.sonic.hr.repo.TeamRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMembershipRepository membershipRepository;

    /** Creates a team; the creator becomes its LEADER. A user may create/join any number of teams. */
    @Transactional
    public TeamMembership create(String name, Employee creator) {
        if (creator.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin cannot create a team");
        }
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
}
