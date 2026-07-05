package uz.sonic.hr.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.sonic.hr.employee.EmployeeRepository;
import uz.sonic.hr.team.TeamMembershipRepository;
import uz.sonic.hr.team.TeamRepository;
import uz.sonic.hr.common.dto.Dtos.EmployeeDto;
import uz.sonic.hr.common.dto.Dtos.TeamAdminDto;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final TeamRepository teamRepository;
    private final EmployeeRepository employeeRepository;
    private final TeamMembershipRepository membershipRepository;

    @GetMapping("/teams")
    public List<TeamAdminDto> teams() {
        return teamRepository.findAll().stream()
                .map(team -> new TeamAdminDto(team.getId(), team.getName(),
                        membershipRepository.countByTeamId(team.getId()), team.getCreatedAt()))
                .toList();
    }

    @GetMapping("/employees")
    public List<EmployeeDto> employees() {
        return employeeRepository.findAll().stream()
                .map(e -> EmployeeDto.from(e,
                        membershipRepository.findAllByEmployeeIdOrderByJoinedAtAsc(e.getId())))
                .toList();
    }
}
