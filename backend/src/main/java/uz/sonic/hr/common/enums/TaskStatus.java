package uz.sonic.hr.common.enums;

public enum TaskStatus {
    /** Self-reported by a member as "what I'm working on" — waiting for a leader to confirm it's a real task. */
    PENDING,
    OPEN,
    IN_PROGRESS,
    /** Submitted by the assignee, waiting for reviewer/manager approval. */
    TESTING,
    DONE,
    CANCELLED
}
