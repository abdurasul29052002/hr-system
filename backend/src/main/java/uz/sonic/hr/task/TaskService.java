package uz.sonic.hr.task;
import uz.sonic.hr.team.TeamService;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
import uz.sonic.hr.task.TagRepository;
import uz.sonic.hr.task.TaskRepository;
import uz.sonic.hr.team.TeamMembershipRepository;
import uz.sonic.hr.common.dto.Dtos.TaskDto;
import uz.sonic.hr.common.dto.Dtos.TaskRequest;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final TagRepository tagRepository;
    private final TeamMembershipRepository membershipRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Leader/manager creates a task. It may be assigned to a member and given a reviewer up front, but
     * assigning does NOT start it — it stays OPEN until the assignee presses "Start".
     */
    @Transactional
    public TaskDto create(TaskRequest request, TeamMembership actor) {
        TeamService.requireManager(actor);
        Team team = actor.getTeam();
        Task task = Task.builder()
                .title(request.title())
                .description(request.description())
                .priority(request.priority() != null ? request.priority() : TaskPriority.MEDIUM)
                .deadline(request.deadline())
                .team(team)
                .tags(resolveTags(request.tagIds() == null ? List.of() : request.tagIds(), team))
                .createdBy(actor.getEmployee())
                .build();
        if (request.assigneeId() != null) {
            task.setAssignee(getActiveTeammate(request.assigneeId(), team));
        }
        task.setReviewer(resolveReviewer(request.reviewerId(), team, task.getAssignee()));
        task = taskRepository.save(task);
        eventPublisher.publishEvent(new TaskEvents.TaskCreated(
                task.getId(), team.getId(), task.getTitle(), task.getPriority(), task.getDeadline(),
                actor.getEmployee().getFullName(), actor.getEmployee().getId(),
                task.getAssignee() != null ? task.getAssignee().getId() : null));
        if (task.getAssignee() != null) {
            eventPublisher.publishEvent(new TaskEvents.TaskAssigned(
                    task.getId(), team.getId(), task.getTitle(), task.getAssignee().getId(),
                    actor.getEmployee().getFullName(), actor.getEmployee().getId()));
        }
        return TaskDto.from(task);
    }

    /** Leader/manager assigns an open task to a member. Assigning does NOT start it — the assignee presses "Start". */
    @Transactional
    public TaskDto assign(Long id, Long employeeId, TeamMembership actor) {
        TeamService.requireManager(actor);
        Task task = getInTeam(id, actor);
        if (task.getStatus() != TaskStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task is not open");
        }
        Employee assignee = getActiveTeammate(employeeId, task.getTeam());
        if (task.getReviewer() != null && task.getReviewer().getId().equals(assignee.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignee and reviewer must be different people");
        }
        task.setAssignee(assignee);
        task = taskRepository.save(task);
        eventPublisher.publishEvent(new TaskEvents.TaskAssigned(
                task.getId(), task.getTeam().getId(), task.getTitle(), assignee.getId(),
                actor.getEmployee().getFullName(), actor.getEmployee().getId()));
        return TaskDto.from(task);
    }

    /** Leader/manager edits task metadata (title, description, priority, deadline, tags, reviewer). No status change. */
    @Transactional
    public TaskDto update(Long id, TaskRequest request, TeamMembership actor) {
        TeamService.requireManager(actor);
        Task task = getInTeam(id, actor);
        task.setTitle(request.title());
        task.setDescription(request.description());
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        task.setDeadline(request.deadline());
        if (request.tagIds() != null) {
            task.setTags(resolveTags(request.tagIds(), task.getTeam()));
        }
        task.setReviewer(resolveReviewer(request.reviewerId(), task.getTeam(), task.getAssignee()));
        return TaskDto.from(taskRepository.save(task));
    }

    /**
     * Begin work on an OPEN task: an unassigned task is grabbed from the pool, or the assigned member
     * "Starts" their own task. Either way it moves to IN_PROGRESS. You cannot take someone else's task.
     */
    @Transactional
    public TaskDto take(Long id, TeamMembership actor) {
        Task task = getInTeam(id, actor);
        if (task.getStatus() != TaskStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task is not open");
        }
        Employee worker = actor.getEmployee();
        boolean assignedToMe = task.getAssignee() != null && task.getAssignee().getId().equals(worker.getId());
        if (task.getAssignee() != null && !assignedToMe && !TeamService.isManagerOrLeader(actor)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task is assigned to someone else");
        }
        task.setStatus(TaskStatus.IN_PROGRESS);
        if (task.getAssignee() == null) {
            task.setAssignee(worker);
        }
        task.setTakenAt(Instant.now());
        task = taskRepository.save(task);
        eventPublisher.publishEvent(new TaskEvents.TaskTaken(
                task.getId(), task.getTeam().getId(), task.getTitle(),
                task.getAssignee().getFullName(), worker.getId()));
        return TaskDto.from(task);
    }

    /**
     * Assignee finishes the work: the task moves to TESTING and waits for approval.
     * A leader/manager completing a task skips the review and goes straight to DONE.
     */
    @Transactional
    public TaskDto complete(Long id, TeamMembership actor) {
        Task task = getInTeam(id, actor);
        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task is not in progress");
        }
        Employee employee = actor.getEmployee();
        boolean isAssignee = task.getAssignee() != null && task.getAssignee().getId().equals(employee.getId());
        boolean manager = TeamService.isManagerOrLeader(actor);
        if (!isAssignee && !manager) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the assignee can complete this task");
        }
        String workerName = task.getAssignee() != null ? task.getAssignee().getFullName() : employee.getFullName();
        if (manager) {
            task.setStatus(TaskStatus.DONE);
            task.setSubmittedAt(task.getSubmittedAt() != null ? task.getSubmittedAt() : Instant.now());
            task.setCompletedAt(Instant.now());
            task = taskRepository.save(task);
            eventPublisher.publishEvent(new TaskEvents.TaskCompleted(
                    task.getId(), task.getTeam().getId(), task.getTitle(), workerName, employee.getId()));
        } else {
            task.setStatus(TaskStatus.TESTING);
            task.setSubmittedAt(Instant.now());
            task = taskRepository.save(task);
            eventPublisher.publishEvent(new TaskEvents.TaskSubmitted(
                    task.getId(), task.getTeam().getId(), task.getTitle(), workerName, employee.getId()));
        }
        return TaskDto.from(task);
    }

    /** The designated reviewer (or a manager) approves a task in review: TESTING → DONE. */
    @Transactional
    public TaskDto approve(Long id, TeamMembership actor) {
        Task task = getInTeam(id, actor);
        if (task.getStatus() != TaskStatus.TESTING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task is not in review");
        }
        requireReviewerOrManager(task, actor);
        task.setStatus(TaskStatus.DONE);
        task.setCompletedAt(Instant.now());
        task.setReviewer(actor.getEmployee()); // record who actually reviewed
        task = taskRepository.save(task);
        eventPublisher.publishEvent(new TaskEvents.TaskApproved(
                task.getId(), task.getTeam().getId(), task.getTitle(),
                task.getAssignee() != null ? task.getAssignee().getId() : null,
                actor.getEmployee().getFullName(), actor.getEmployee().getId()));
        return TaskDto.from(task);
    }

    /** The designated reviewer (or a manager) returns a task for rework: TESTING → IN_PROGRESS. */
    @Transactional
    public TaskDto reject(Long id, TeamMembership actor) {
        Task task = getInTeam(id, actor);
        if (task.getStatus() != TaskStatus.TESTING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task is not in review");
        }
        requireReviewerOrManager(task, actor);
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setSubmittedAt(null);
        task.setReviewer(actor.getEmployee()); // record who actually reviewed
        task = taskRepository.save(task);
        eventPublisher.publishEvent(new TaskEvents.TaskRejected(
                task.getId(), task.getTeam().getId(), task.getTitle(),
                task.getAssignee() != null ? task.getAssignee().getId() : null,
                actor.getEmployee().getId()));
        return TaskDto.from(task);
    }

    @Transactional
    public TaskDto cancel(Long id, TeamMembership actor) {
        TeamService.requireManager(actor);
        Task task = getInTeam(id, actor);
        if (task.getStatus() == TaskStatus.DONE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Completed task cannot be cancelled");
        }
        task.setStatus(TaskStatus.CANCELLED);
        return TaskDto.from(taskRepository.save(task));
    }

    /** Assignee releases a task back to the open pool. */
    @Transactional
    public TaskDto release(Long id, TeamMembership actor) {
        Task task = getInTeam(id, actor);
        boolean isAssignee = task.getAssignee() != null
                && task.getAssignee().getId().equals(actor.getEmployee().getId());
        if (!isAssignee && !TeamService.isManagerOrLeader(actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the assignee can release this task");
        }
        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task is not in progress");
        }
        task.setStatus(TaskStatus.OPEN);
        task.setAssignee(null);
        task.setTakenAt(null);
        return TaskDto.from(taskRepository.save(task));
    }

    /**
     * A member self-reports what they are working on. It becomes a PENDING proposal (assigned to them),
     * visible only to the proposer and the team's leaders/managers, until a leader confirms it as a task.
     */
    @Transactional
    public TaskDto propose(TaskRequest request, TeamMembership actor) {
        Team team = actor.getTeam();
        Employee me = actor.getEmployee();
        Task task = taskRepository.save(Task.builder()
                .title(request.title())
                .description(request.description())
                .priority(request.priority() != null ? request.priority() : TaskPriority.MEDIUM)
                .deadline(request.deadline())
                .team(team)
                .createdBy(me)
                .assignee(me)
                .status(TaskStatus.PENDING)
                .build());
        eventPublisher.publishEvent(new TaskEvents.TaskProposed(
                task.getId(), team.getId(), task.getTitle(), me.getFullName(), me.getId()));
        return TaskDto.from(task);
    }

    /** Leader/manager confirms a proposal: PENDING → IN_PROGRESS, now visible to the whole team. */
    @Transactional
    public TaskDto approveProposal(Long id, TeamMembership actor) {
        TeamService.requireManager(actor);
        Task task = getInTeam(id, actor);
        if (task.getStatus() != TaskStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task is not a pending proposal");
        }
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setTakenAt(Instant.now());
        return TaskDto.from(taskRepository.save(task));
    }

    /** Leader/manager declines a proposal: it is deleted. */
    @Transactional
    public void rejectProposal(Long id, TeamMembership actor) {
        TeamService.requireManager(actor);
        Task task = getInTeam(id, actor);
        if (task.getStatus() != TaskStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task is not a pending proposal");
        }
        taskRepository.delete(task);
    }

    @Transactional(readOnly = true)
    public List<TaskDto> list(TaskStatus status, Long assigneeId, TeamMembership viewer) {
        Long teamId = viewer.getTeam().getId();
        List<Task> tasks;
        if (assigneeId != null && status != null) {
            tasks = taskRepository.findAllByTeamIdAndAssigneeIdAndStatusOrderByCreatedAtDesc(teamId, assigneeId, status);
        } else if (assigneeId != null) {
            tasks = taskRepository.findAllByTeamIdAndAssigneeIdOrderByCreatedAtDesc(teamId, assigneeId);
        } else if (status != null) {
            tasks = taskRepository.findAllByTeamIdAndStatusOrderByPriorityDescCreatedAtDesc(teamId, status);
        } else {
            tasks = taskRepository.findAllByTeamIdOrderByCreatedAtDesc(teamId);
        }
        boolean manager = TeamService.isManagerOrLeader(viewer);
        Long myId = viewer.getEmployee().getId();
        return tasks.stream()
                // PENDING proposals are visible only to their proposer and the team's leaders/managers.
                .filter(t -> t.getStatus() != TaskStatus.PENDING || manager
                        || (t.getAssignee() != null && t.getAssignee().getId().equals(myId)))
                .map(TaskDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskDto getDto(Long id, TeamMembership viewer) {
        return TaskDto.from(getInTeam(id, viewer));
    }

    private void requireReviewerOrManager(Task task, TeamMembership actor) {
        boolean isReviewer = task.getReviewer() != null
                && task.getReviewer().getId().equals(actor.getEmployee().getId());
        if (!isReviewer && !TeamService.isManagerOrLeader(actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the assigned reviewer or a manager can review this task");
        }
    }

    /** Resolves the optional reviewer, ensuring it is an active teammate and not the assignee. */
    private Employee resolveReviewer(Long reviewerId, Team team, Employee assignee) {
        if (reviewerId == null) {
            return null;
        }
        Employee reviewer = getActiveTeammate(reviewerId, team);
        if (assignee != null && assignee.getId().equals(reviewer.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The reviewer must be different from the assignee");
        }
        return reviewer;
    }

    private Employee getActiveTeammate(Long employeeId, Team team) {
        return membershipRepository.findByEmployeeIdAndTeamId(employeeId, team.getId())
                .map(TeamMembership::getEmployee)
                .filter(Employee::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Assignee must be an active member of your team"));
    }

    private HashSet<Tag> resolveTags(List<Long> tagIds, Team team) {
        if (tagIds.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(tagRepository.findAllByIdInAndTeamId(tagIds, team.getId()));
    }

    private Task getInTeam(Long id, TeamMembership viewer) {
        Long teamId = viewer.getTeam().getId();
        return taskRepository.findById(id)
                .filter(task -> task.getTeam().getId().equals(teamId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }
}
