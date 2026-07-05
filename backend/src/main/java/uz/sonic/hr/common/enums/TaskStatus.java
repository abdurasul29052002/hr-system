package uz.sonic.hr.common.enums;

public enum TaskStatus {
    OPEN,
    IN_PROGRESS,
    /** Submitted by the assignee, waiting for leader/manager approval. */
    TESTING,
    DONE,
    CANCELLED
}
