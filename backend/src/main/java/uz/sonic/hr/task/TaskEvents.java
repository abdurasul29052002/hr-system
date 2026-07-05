package uz.sonic.hr.task;

import uz.sonic.hr.common.enums.TaskPriority;

import java.time.LocalDate;

public final class TaskEvents {

    private TaskEvents() {
    }

    public record TaskCreated(Long taskId, Long teamId, String title, TaskPriority priority, LocalDate deadline,
                              String creatorName, Long actorId, Long assigneeId) {
    }

    /** An existing open task was assigned to a member by a leader/manager. */
    public record TaskAssigned(Long taskId, Long teamId, String title, Long assigneeId, String assignerName,
                               Long actorId) {
    }

    public record TaskTaken(Long taskId, Long teamId, String title, String workerName, Long actorId) {
    }

    /** Assignee submitted the task for review (IN_PROGRESS → TESTING). */
    public record TaskSubmitted(Long taskId, Long teamId, String title, String workerName, Long actorId) {
    }

    /** Leader/manager approved a task in review (TESTING → DONE). */
    public record TaskApproved(Long taskId, Long teamId, String title, Long assigneeId, String approverName,
                               Long actorId) {
    }

    /** Leader/manager returned a task for rework (TESTING → IN_PROGRESS). */
    public record TaskRejected(Long taskId, Long teamId, String title, Long assigneeId, Long actorId) {
    }

    public record TaskCompleted(Long taskId, Long teamId, String title, String workerName, Long actorId) {
    }
}
