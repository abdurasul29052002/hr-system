package uz.sonic.hr.team;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import uz.sonic.hr.common.dto.Dtos.TeamJoinRequestDto;
import uz.sonic.hr.common.dto.Dtos.TeamSearchDto;
import uz.sonic.hr.common.security.CurrentMembership;
import uz.sonic.hr.common.security.CurrentUser;

import java.util.List;

@RestController
@RequestMapping("/api/team-join-requests")
@RequiredArgsConstructor
public class TeamJoinRequestController {

    private final TeamJoinRequestService joinRequestService;
    private final CurrentUser currentUser;
    private final CurrentMembership currentMembership;

    /** Search teams by name so a team-less user can ask to join one. */
    @GetMapping("/search")
    public List<TeamSearchDto> search(@RequestParam String q) {
        return joinRequestService.search(q, currentUser.get());
    }

    /** Send a request to join the given team. */
    @PostMapping("/{teamId}")
    public TeamJoinRequestDto create(@PathVariable Long teamId) {
        return joinRequestService.create(teamId, currentUser.get());
    }

    /** Leader/manager view of pending requests for the current team. */
    @GetMapping
    public List<TeamJoinRequestDto> pending() {
        return joinRequestService.listPending(currentMembership.get());
    }

    /** Leader/manager approves a join request. */
    @PostMapping("/{requestId}/approve")
    public TeamJoinRequestDto approve(@PathVariable Long requestId) {
        return joinRequestService.approve(requestId, currentMembership.get());
    }

    /** Leader/manager rejects a join request. */
    @PostMapping("/{requestId}/reject")
    public TeamJoinRequestDto reject(@PathVariable Long requestId) {
        return joinRequestService.reject(requestId, currentMembership.get());
    }
}
