package uz.sonic.hr.common.enums;

public enum NotificationType {
    MENTIONED,           // User was mentioned in a comment
    TASK_ASSIGNED,       // Task was assigned to user
    TASK_COMPLETED,      // Task was completed
    TASK_APPROVED,       // Task was approved (from TESTING to DONE)
    TASK_REJECTED,       // Task was rejected (from TESTING back to IN_PROGRESS)
    INVITE_RECEIVED,     // User received a team invite
    COMMENT_ADDED        // New comment on a task user is involved with
}
