package uz.sonic.hr.common.enums;

public enum NotificationType {
    MENTIONED,           // User was mentioned in a comment
    TASK_ASSIGNED,       // Task was assigned to user
    TASK_COMPLETED,      // Task was completed
    TASK_APPROVED,       // Task was approved (from TESTING to DONE)
    TASK_REJECTED,       // Task was rejected (from TESTING back to IN_PROGRESS)
    TASK_PROPOSED,       // A member self-reported work; a leader must confirm it as a task
    TEAM_JOIN_REQUESTED, // A user asked to join a team; leaders should approve or reject it
    TEAM_JOIN_APPROVED,  // A leader approved a user's request to join the team
    TEAM_JOIN_REJECTED,  // A leader rejected a user's request to join the team
    INVITE_RECEIVED,     // User received a team invite
    COMMENT_ADDED        // New comment on a task user is involved with
}
