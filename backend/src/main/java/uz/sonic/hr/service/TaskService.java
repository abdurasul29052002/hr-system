package uz.sonic.hr.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uz.sonic.hr.entity.*;
import uz.sonic.hr.repo.TagRepository;
import uz.sonic.hr.repo.TaskRepository;
import uz.sonic.hr.repo.TeamMembershipRepository;
import uz.sonic.hr.web.dto.Dtos.TaskDto;
import uz.sonic.hr.web.dto.Dtos.TaskRequest;

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

    /** Leader/manager creates a task; optionally assigned to a team member right away. */
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
            Employee assignee = getActiveTeammate(request.assigneeId(), team);
            task.setAssignee(assignee);
            task.setStatus(TaskStatus.IN_PROGRESS);
            task.setTakenAt(Instant.now());
        }
        task = taskRepository.save(task);
        eventPublisher.publishEvent(new TaskEvents.TaskCreated(
                task.getId(), team.getId(), task.getTitle(), task.getPriority(), task.getDeadline(),
                actor.getEmployee().getFullName(), actor.getEmployee().getId(),
                task.getAssignee() != null ? task.getAssignee().getId() : null));
        return TaskDto.from(task);
    }

    /** Leader/manager assigns an open task to an active team member. */
    @Transactional
    public TaskDto assign(Long id, Long employeeId, TeamMembership actor) {
        TeamService.requireManager(actor);
        Task task = getInTeam(id, actor);
        if (task.getStatus() != TaskStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task is not open");
        }
        Employee assignee = getActiveTeammate(employeeId, task.getTeam());
        task.setAssignee(assignee);
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setTakenAt(Instant.now());
        task = taskRepository.save(task);
        eventPublisher.publishEvent(new TaskEvents.TaskAssigned(
                task.getId(), task.getTeam().getId(), task.getTitle(), assignee.getId(),
                actor.getEmployee().getFullName(), actor.getEmployee().getId()));
        return TaskDto.from(task);
    }

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
        return TaskDto.from(taskRepository.save(task));
    }

    @Transactional
    public TaskDto take(Long id, TeamMembership actor) {
        Task task = getInTeam(id, actor);
        if (task.getStatus() != TaskStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task is not open");
        }
        Employee worker = actor.getEmployee();
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setAssignee(worker);
        task.setTakenAt(Instant.now());
        task = taskRepository.save(task);
        eventPublisher.publishEvent(new TaskEvents.TaskTaken(
                task.getId(), task.getTeam().getId(), task.getTitle(), worker.getFullName(), worker.getId()));
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

    /** Leader/manager approves a task in review: TESTING → DONE. */
    @Transactional
    public TaskDto approve(Long id, TeamMembership actor) {
        TeamService.requireManager(actor);
        Task task = getInTeam(id, actor);
        if (task.getStatus() != TaskStatus.TESTING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task is not in review");
        }
        task.setStatus(TaskStatus.DONE);
        task.setCompletedAt(Instant.now());
        task = taskRepository.save(task);
        eventPublisher.publishEvent(new TaskEvents.TaskApproved(
                task.getId(), task.getTeam().getId(), task.getTitle(),
                task.getAssignee() != null ? task.getAssignee().getId() : null,
                actor.getEmployee().getFullName(), actor.getEmployee().getId()));
        return TaskDto.from(task);
    }

    /** Leader/manager returns a task for rework: TESTING → IN_PROGRESS. */
    @Transactional
    public TaskDto reject(Long id, TeamMembership actor) {
        TeamService.requireManager(actor);
        Task task = getInTeam(id, actor);
        if (task.getStatus() != TaskStatus.TESTING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task is not in review");
        }
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setSubmittedAt(null);
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
        return tasks.stream().map(TaskDto::from).toList();
    }

    @Transactional(readOnly = true)
    public TaskDto getDto(Long id, TeamMembership viewer) {
        return TaskDto.from(getInTeam(id, viewer));
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
