package uz.sonic.hr.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import uz.sonic.hr.entity.Employee;
import uz.sonic.hr.entity.TeamInvite;
import uz.sonic.hr.repo.TeamMembershipRepository;
import uz.sonic.hr.security.CurrentMembership;
import uz.sonic.hr.security.CurrentUser;
import uz.sonic.hr.service.InviteService;
import uz.sonic.hr.web.dto.Dtos.*;

import java.util.List;

@RestController
@RequestMapping("/api/invites")
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;
    private final CurrentMembership currentMembership;
    private final CurrentUser currentUser;
    private final TeamMembershipRepository membershipRepository;

    /** Creates an invite link for the current team (X-Team-Id). */
    @PostMapping
    public InviteDto create(@Valid @RequestBody CreateInviteRequest request) {
        return InviteDto.from(inviteService.create(request.role(), currentMembership.get()));
    }

    @GetMapping
    public List<InviteDto> list() {
        return inviteService.list(currentMembership.get()).stream().map(InviteDto::from).toList();
    }

    @DeleteMapping("/{id}")
    public void revoke(@PathVariable Long id) {
        inviteService.revoke(id, currentMembership.get());
    }

    /** Invite details for the join page (any authenticated user). */
    @GetMapping("/token/{token}")
    public InviteInfo info(@PathVariable String token) {
        TeamInvite invite = inviteService.getUsable(token);
        Employee employee = currentUser.get();
        boolean alreadyMember = membershipRepository.existsByEmployeeIdAndTeamId(
                employee.getId(), invite.getTeam().getId());
        return new InviteInfo(invite.getTeam().getName(), invite.getRole(), alreadyMember);
    }

    /** The current user joins the invite's team. */
    @PostMapping("/token/{token}/accept")
    public MyTeam accept(@PathVariable String token) {
        return MyTeam.from(inviteService.accept(token, currentUser.get()));
    }
}
