package uz.sonic.hr.team;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import uz.sonic.hr.employee.Employee;
import uz.sonic.hr.common.security.CurrentUser;
import uz.sonic.hr.team.TeamService;
import uz.sonic.hr.common.dto.Dtos.CreateTeamRequest;
import uz.sonic.hr.common.dto.Dtos.MyTeam;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;
    private final CurrentUser currentUser;

    /** Creates a team; the current user becomes its LEADER. Returns the new membership. */
    @PostMapping
    public MyTeam create(@Valid @RequestBody CreateTeamRequest request) {
        return MyTeam.from(teamService.create(request.name(), currentUser.get()));
    }

    @GetMapping("/my")
    public List<MyTeam> my() {
        Employee employee = currentUser.get();
        return teamService.membershipsOf(employee).stream().map(MyTeam::from).toList();
    }
}
