package uz.sonic.hr.entity;

public enum TaskStatus {
    OPEN,
    IN_PROGRESS,
    /** Submitted by the assignee, waiting for leader/manager approval. */
    TESTING,
    DONE,
    CANCELLED
}
