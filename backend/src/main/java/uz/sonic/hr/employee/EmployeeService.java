package uz.sonic.hr.employee;
import uz.sonic.hr.team.TeamService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uz.sonic.hr.employee.Employee;
import uz.sonic.hr.common.enums.Language;
import uz.sonic.hr.common.enums.Role;
import uz.sonic.hr.team.MemberLabel;
import uz.sonic.hr.team.MemberLabelRepository;
import uz.sonic.hr.team.Team;
import uz.sonic.hr.team.TeamMembership;
import uz.sonic.hr.employee.EmployeeRepository;
import uz.sonic.hr.team.TeamMembershipRepository;
import uz.sonic.hr.team.TeamJoinRequestRepository;
import uz.sonic.hr.notification.NotificationRepository;
import uz.sonic.hr.common.dto.Dtos.*;

import java.security.SecureRandom;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmployeeRepository employeeRepository;
    private final TeamMembershipRepository membershipRepository;
    private final MemberLabelRepository labelRepository;
    private final PasswordEncoder passwordEncoder;
    private final TeamService teamService;
    private final TeamJoinRequestRepository joinRequestRepository;
    private final NotificationRepository notificationRepository;

    /**
     * Self-registration: create a team-less account first. After login, the user chooses whether to
     * join an existing team (by sending a request) or create their own team. This avoids forcing new
     * users through an immediate team-creation wall.
     */
    @Transactional
    public Employee register(RegisterRequest request) {
        if (employeeRepository.existsByUsername(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        return employeeRepository.save(Employee.builder()
                .fullName(request.fullName())
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .phone(request.phone())
                .language(request.language() != null ? request.language() : Language.EN)
                .telegramLinkCode(generateUniqueLinkCode())
                .build());
    }

    /** Creates a brand-new user and adds them to the actor's team. */
    @Transactional
    public TeamMembership createInTeam(NewMemberRequest request, TeamMembership actor) {
        TeamService.requireManager(actor);
        Role role = validateAssignableRole(request.role(), actor);
        if (employeeRepository.existsByUsername(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        Employee employee = employeeRepository.save(Employee.builder()
                .fullName(request.fullName())
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .phone(request.phone())
                .language(request.language() != null ? request.language() : Language.EN)
                .telegramLinkCode(generateUniqueLinkCode())
                .build());
        return membershipRepository.save(TeamMembership.builder()
                .employee(employee)
                .team(actor.getTeam())
                .role(role)
                .position(request.position())
                .labels(resolveLabels(request.labelIds(), actor.getTeam()))
                .build());
    }

    /** Adds an already-registered user to the actor's team by username. */
    @Transactional
    public TeamMembership addExisting(AddMemberRequest request, TeamMembership actor) {
        TeamService.requireManager(actor);
        Role role = validateAssignableRole(request.role(), actor);
        Employee employee = employeeRepository.findByUsername(request.username().trim())
                .filter(Employee::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (membershipRepository.existsByEmployeeIdAndTeamId(employee.getId(), actor.getTeam().getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already in this team");
        }
        return membershipRepository.save(TeamMembership.builder()
                .employee(employee)
                .team(actor.getTeam())
                .role(role)
                .position(request.position())
                .labels(resolveLabels(request.labelIds(), actor.getTeam()))
                .build());
    }

    /** Updates a member's per-team role/position. Role changes are leader-only. */
    @Transactional
    public TeamMembership updateMember(Long employeeId, UpdateMemberRequest request, TeamMembership actor) {
        TeamService.requireManager(actor);
        TeamMembership target = getMembership(employeeId, actor);
        requireManagerCanTouch(actor, target);
        if (request.role() != null && request.role() != target.getRole()) {
            TeamService.requireLeader(actor);
            if (target.getEmployee().getId().equals(actor.getEmployee().getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot change your own role");
            }
            if (target.getRole() == Role.LEADER && countLeaders(actor) <= 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The team must keep at least one leader");
            }
            target.setRole(request.role());
        }
        target.setPosition(request.position());
        if (request.labelIds() != null) {
            target.setLabels(resolveLabels(request.labelIds(), actor.getTeam()));
        }
        return membershipRepository.save(target);
    }

    private Set<MemberLabel> resolveLabels(List<Long> labelIds, Team team) {
        if (labelIds == null || labelIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(labelRepository.findAllByIdInAndTeamId(labelIds, team.getId()));
    }

    /** Removes a member from the actor's team (the account itself remains). */
    @Transactional
    public void removeFromTeam(Long employeeId, TeamMembership actor) {
        TeamService.requireManager(actor);
        TeamMembership target = getMembership(employeeId, actor);
        requireManagerCanTouch(actor, target);
        if (target.getEmployee().getId().equals(actor.getEmployee().getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot remove yourself");
        }
        if (target.getRole() == Role.LEADER && countLeaders(actor) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The team must keep at least one leader");
        }
        membershipRepository.delete(target);
    }

    /**
     * Self-service account deletion — deactivate + anonymize. The employee ROW is kept: their authored
     * tasks/comments point at it via NOT-NULL FKs (created_by / author_id), so a hard delete would
     * either break those or wipe shared-team history. Instead we delete the teams where they are the
     * only member (full cascade), leave every other team (blocking if they are its last leader — they
     * must hand it over first), drop their own join requests and the notifications addressed to them,
     * then scrub personal data and disable login (username is freed as {@code deleted_<id>}).
     */
    @Transactional
    public void deleteOwnAccount(Employee me) {
        List<TeamMembership> memberships = membershipRepository.findAllByEmployeeIdOrderByJoinedAtAsc(me.getId());
        // Validate everything first so a block leaves the account fully intact.
        for (TeamMembership m : memberships) {
            boolean shared = membershipRepository.countByTeamId(m.getTeam().getId()) > 1;
            if (shared && m.getRole() == Role.LEADER
                    && membershipRepository.countByTeamIdAndRole(m.getTeam().getId(), Role.LEADER) <= 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "You are the last leader of \"" + m.getTeam().getName()
                                + "\". Transfer leadership or delete this team before deleting your account.");
            }
        }
        // Apply: solo teams vanish entirely; shared teams simply lose this membership.
        for (TeamMembership m : memberships) {
            if (membershipRepository.countByTeamId(m.getTeam().getId()) <= 1) {
                teamService.deleteTeam(m.getTeam().getId());
            } else {
                membershipRepository.delete(m);
            }
        }
        joinRequestRepository.deleteByEmployeeId(me.getId());
        notificationRepository.deleteByEmployeeId(me.getId());
        me.setActive(false);
        me.setFullName("Deleted user");
        me.setUsername("deleted_" + me.getId());
        me.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        me.setPhone(null);
        me.setTelegramChatId(null);
        me.setTelegramLinkCode(null);
        me.setBotTeamId(null);
        employeeRepository.save(me);
    }

    /** Unlinks Telegram and issues a fresh link code for a member of the actor's team. */
    @Transactional
    public TeamMembership resetTelegram(Long employeeId, TeamMembership actor) {
        TeamService.requireManager(actor);
        TeamMembership target = getMembership(employeeId, actor);
        Employee employee = target.getEmployee();
        employee.setTelegramChatId(null);
        employee.setTelegramLinkCode(generateUniqueLinkCode());
        employeeRepository.save(employee);
        return target;
    }

    @Transactional(readOnly = true)
    public List<TeamMembership> listMembers(TeamMembership actor) {
        TeamService.requireManager(actor);
        return membershipRepository.findAllByTeamIdWithEmployee(actor.getTeam().getId());
    }

    /**
     * Team roster for @mention autocomplete. Any team member may read it (no manager check) — the
     * caller is already proven to belong to the team by resolving {@code actor}. Returns memberships
     * so the controller can project only the non-sensitive fields (see MentionMemberDto).
     */
    @Transactional(readOnly = true)
    public List<TeamMembership> listMentionable(TeamMembership actor) {
        return membershipRepository.findAllByTeamIdWithEmployee(actor.getTeam().getId());
    }

    private long countLeaders(TeamMembership actor) {
        return membershipRepository.countByTeamIdAndRole(actor.getTeam().getId(), Role.LEADER);
    }

    private static void requireManagerCanTouch(TeamMembership actor, TeamMembership target) {
        if (actor.getRole() == Role.MANAGER && target.getRole() != Role.MEMBER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A manager can only manage members");
        }
    }

    private Role validateAssignableRole(Role requested, TeamMembership actor) {
        Role role = requested != null ? requested : Role.MEMBER;
        if (role != Role.MEMBER) {
            TeamService.requireLeader(actor);
        }
        return role;
    }

    private TeamMembership getMembership(Long employeeId, TeamMembership actor) {
        return membershipRepository.findByEmployeeIdAndTeamId(employeeId, actor.getTeam().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
    }

    private String generateUniqueLinkCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            String code = sb.toString();
            if (employeeRepository.findByTelegramLinkCode(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique telegram link code");
    }
}
