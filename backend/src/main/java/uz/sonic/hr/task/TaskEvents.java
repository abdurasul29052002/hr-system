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

    /**
     * A member self-reported what they are working on; leaders must confirm it becomes a real task.
     * Carries the proposal's details so the Telegram card can be rendered without re-querying (and
     * without a LazyInitializationException in the async listener), mirroring {@link TaskCreated}.
     */
    public record TaskProposed(Long taskId, Long teamId, String title, String description, TaskPriority priority,
                               LocalDate deadline, String proposerName, Long proposerId) {
    }

    /** A leader/manager confirmed a member's proposal (PENDING → IN_PROGRESS). Notifies the proposer. */
    public record ProposalApproved(Long taskId, Long teamId, String title, Long proposerId, String approverName,
                                   Long actorId) {
    }

    /** A leader/manager declined a member's proposal (the PENDING task is deleted). Notifies the proposer. */
    public record ProposalRejected(Long taskId, Long teamId, String title, Long proposerId, String actorName,
                                   Long actorId) {
    }
}
