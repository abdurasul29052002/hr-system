package uz.sonic.hr.team;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uz.sonic.hr.common.enums.*;
import uz.sonic.hr.common.entity.*;
import uz.sonic.hr.employee.*;
import uz.sonic.hr.admin.*;
import uz.sonic.hr.team.*;
import uz.sonic.hr.task.*;
import uz.sonic.hr.notification.*;
import uz.sonic.hr.ticket.*;
import uz.sonic.hr.team.TeamInviteRepository;
import uz.sonic.hr.team.TeamMembershipRepository;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InviteService {

    private static final String TOKEN_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration TTL = Duration.ofDays(7);

    private final TeamInviteRepository inviteRepository;
    private final TeamMembershipRepository membershipRepository;

    @Transactional
    public TeamInvite create(Role requestedRole, TeamMembership actor) {
        TeamService.requireManager(actor);
        Role role = requestedRole != null ? requestedRole : Role.MEMBER;
        if (role != Role.MEMBER) {
            TeamService.requireLeader(actor);
        }
        return inviteRepository.save(TeamInvite.builder()
                .token(generateToken())
                .team(actor.getTeam())
                .role(role)
                .createdBy(actor.getEmployee())
                .expiresAt(Instant.now().plus(TTL))
                .build());
    }

    @Transactional(readOnly = true)
    public List<TeamInvite> list(TeamMembership actor) {
        TeamService.requireManager(actor);
        return inviteRepository.findAllByTeamIdAndRevokedFalseOrderByCreatedAtDesc(actor.getTeam().getId());
    }

    @Transactional
    public void revoke(Long id, TeamMembership actor) {
        TeamService.requireManager(actor);
        TeamInvite invite = inviteRepository.findById(id)
                .filter(i -> i.getTeam().getId().equals(actor.getTeam().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invite not found"));
        invite.setRevoked(true);
        inviteRepository.save(invite);
    }

    /** Invite details shown on the join page. */
    @Transactional(readOnly = true)
    public TeamInvite getUsable(String token) {
        return inviteRepository.findByToken(token)
                .filter(TeamInvite::isUsable)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invite is invalid or expired"));
    }

    /** The current user joins the invite's team. */
    @Transactional
    public TeamMembership accept(String token, Employee employee) {
        TeamInvite invite = getUsable(token);
        if (membershipRepository.existsByEmployeeIdAndTeamId(employee.getId(), invite.getTeam().getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You are already in this team");
        }
        return membershipRepository.save(TeamMembership.builder()
                .employee(employee)
                .team(invite.getTeam())
                .role(invite.getRole())
                .build());
    }

    private String generateToken() {
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            sb.append(TOKEN_CHARS.charAt(RANDOM.nextInt(TOKEN_CHARS.length())));
        }
        return sb.toString();
    }
}
