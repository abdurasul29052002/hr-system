package uz.sonic.hr.team;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uz.sonic.hr.common.dto.Dtos.TeamJoinRequestDto;
import uz.sonic.hr.common.dto.Dtos.TeamSearchDto;
import uz.sonic.hr.employee.Employee;
import uz.sonic.hr.common.enums.JoinRequestStatus;
import uz.sonic.hr.common.enums.Role;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamJoinRequestService {

    private final TeamRepository teamRepository;
    private final TeamMembershipRepository membershipRepository;
    private final TeamJoinRequestRepository requestRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** Search teams by partial name for a team-less user who wants to ask to join one. */
    @Transactional(readOnly = true)
    public List<TeamSearchDto> search(String query, Employee employee) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        return teamRepository.searchByName(q).stream()
                .filter(team -> !membershipRepository.existsByEmployeeIdAndTeamId(employee.getId(), team.getId()))
                .map(team -> new TeamSearchDto(
                        team.getId(), team.getName(), membershipRepository.countByTeamId(team.getId()), team.getCreatedAt()))
                .toList();
    }

    /** Team-less (or multi-team) user asks to join a team; leaders/managers must approve it. */
    @Transactional
    public TeamJoinRequestDto create(Long teamId, Employee employee) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
        if (membershipRepository.existsByEmployeeIdAndTeamId(employee.getId(), teamId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You are already in this team");
        }
        if (requestRepository.existsByTeamIdAndEmployeeIdAndStatus(teamId, employee.getId(), JoinRequestStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already sent a join request to this team");
        }
        TeamJoinRequest request = requestRepository.save(TeamJoinRequest.builder()
                .team(team)
                .employee(employee)
                .status(JoinRequestStatus.PENDING)
                .build());
        eventPublisher.publishEvent(new JoinRequestEvents.TeamJoinRequested(
                request.getId(), teamId, team.getName(), employee.getId(), employee.getFullName()));
        return TeamJoinRequestDto.from(request);
    }

    /** Leader/manager sees pending requests for the current team. */
    @Transactional(readOnly = true)
    public List<TeamJoinRequestDto> listPending(TeamMembership actor) {
        TeamService.requireManager(actor);
        return requestRepository.findAllByTeamIdAndStatusWithEmployee(actor.getTeam().getId(), JoinRequestStatus.PENDING)
                .stream().map(TeamJoinRequestDto::from).toList();
    }

    /** Approve a pending join request: the employee becomes a MEMBER of the team. */
    @Transactional
    public TeamJoinRequestDto approve(Long requestId, TeamMembership actor) {
        TeamService.requireManager(actor);
        TeamJoinRequest request = getPendingInTeam(requestId, actor.getTeam().getId());
        if (membershipRepository.existsByEmployeeIdAndTeamId(request.getEmployee().getId(), actor.getTeam().getId())) {
            request.setStatus(JoinRequestStatus.APPROVED);
            request.setDecidedAt(Instant.now());
            return TeamJoinRequestDto.from(requestRepository.save(request));
        }
        membershipRepository.save(TeamMembership.builder()
                .employee(request.getEmployee())
                .team(actor.getTeam())
                .role(Role.MEMBER)
                .build());
        request.setStatus(JoinRequestStatus.APPROVED);
        request.setDecidedAt(Instant.now());
        request = requestRepository.save(request);
        eventPublisher.publishEvent(new JoinRequestEvents.TeamJoinApproved(
                request.getId(), actor.getTeam().getId(), request.getEmployee().getId(),
                request.getEmployee().getFullName(), actor.getEmployee().getId()));
        return TeamJoinRequestDto.from(request);
    }

    /** Reject a pending join request. */
    @Transactional
    public TeamJoinRequestDto reject(Long requestId, TeamMembership actor) {
        TeamService.requireManager(actor);
        TeamJoinRequest request = getPendingInTeam(requestId, actor.getTeam().getId());
        request.setStatus(JoinRequestStatus.REJECTED);
        request.setDecidedAt(Instant.now());
        request = requestRepository.save(request);
        eventPublisher.publishEvent(new JoinRequestEvents.TeamJoinRejected(
                request.getId(), actor.getTeam().getId(), request.getEmployee().getId(),
                request.getEmployee().getFullName(), actor.getEmployee().getId()));
        return TeamJoinRequestDto.from(request);
    }

    /**
     * Approve a request when the actor is identified only by their {@link Employee} (e.g. a Telegram
     * callback, which has no X-Team-Id header). The actor's membership is resolved in the request's
     * own team, so authorization still runs against the correct team — never the actor's "current" one.
     */
    @Transactional
    public TeamJoinRequestDto approveByEmployee(Long requestId, Employee actor) {
        return decideByEmployee(requestId, actor, true);
    }

    /** Reject a request identified by the actor's {@link Employee}. See {@link #approveByEmployee}. */
    @Transactional
    public TeamJoinRequestDto rejectByEmployee(Long requestId, Employee actor) {
        return decideByEmployee(requestId, actor, false);
    }

    private TeamJoinRequestDto decideByEmployee(Long requestId, Employee actorEmployee, boolean approve) {
        TeamJoinRequest request = requestRepository.findWithEmployeeAndTeamById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Join request not found"));
        TeamMembership actor = membershipRepository
                .findByEmployeeIdAndTeamId(actorEmployee.getId(), request.getTeam().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "You are not a member of this team"));
        return approve ? approve(requestId, actor) : reject(requestId, actor);
    }

    private TeamJoinRequest getPendingInTeam(Long requestId, Long teamId) {
        // Serialize concurrent deciders on this row: the second approve/reject blocks on the lock until
        // the first commits, then re-reads the (now non-PENDING) status below and is rejected. Without
        // this, both could pass the PENDING check under READ_COMMITTED and both write — double-notifying
        // the requester or leaving them a member while the request reads REJECTED.
        requestRepository.lockById(requestId);
        return requestRepository.findWithEmployeeAndTeamById(requestId)
                .filter(r -> r.getTeam().getId().equals(teamId))
                .filter(r -> r.getStatus() == JoinRequestStatus.PENDING)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Join request not found"));
    }
}
