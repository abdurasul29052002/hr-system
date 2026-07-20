package uz.sonic.hr.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import uz.sonic.hr.common.enums.*;
import uz.sonic.hr.common.entity.*;
import uz.sonic.hr.employee.*;
import uz.sonic.hr.admin.*;
import uz.sonic.hr.team.*;
import uz.sonic.hr.task.*;
import uz.sonic.hr.notification.*;
import uz.sonic.hr.ticket.*;
import uz.sonic.hr.employee.EmployeeRepository;
import uz.sonic.hr.task.TaskCommentRepository;
import uz.sonic.hr.task.TaskRepository;
import uz.sonic.hr.team.TeamInviteRepository;
import uz.sonic.hr.team.TeamMembershipRepository;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final EmployeeRepository employeeRepository;
    private final TaskRepository taskRepository;
    private final TaskCommentRepository commentRepository;
    private final TeamInviteRepository inviteRepository;
    private final TeamMembershipRepository membershipRepository;

    @EventListener
    @Async
    @Transactional
    public void onTaskAssigned(TaskEvents.TaskAssigned event) {
        try {
            Employee assignee = employeeRepository.findById(event.assigneeId()).orElse(null);
            Employee actor = employeeRepository.findById(event.actorId()).orElse(null);
            Task task = taskRepository.findById(event.taskId()).orElse(null);

            if (assignee != null && actor != null && task != null) {
                notificationService.createNotification(
                        NotificationType.TASK_ASSIGNED,
                        assignee,
                        "Task assigned to you",
                        event.assignerName() + " assigned you: " + event.title(),
                        task, null, null, actor
                );
            }
        } catch (Exception e) {
            log.error("Failed to create notification for TaskAssigned", e);
        }
    }

    @EventListener
    @Async
    @Transactional
    public void onTaskApproved(TaskEvents.TaskApproved event) {
        try {
            Employee assignee = employeeRepository.findById(event.assigneeId()).orElse(null);
            Employee actor = employeeRepository.findById(event.actorId()).orElse(null);
            Task task = taskRepository.findById(event.taskId()).orElse(null);

            if (assignee != null && actor != null && task != null) {
                notificationService.createNotification(
                        NotificationType.TASK_APPROVED,
                        assignee,
                        "Task approved",
                        event.approverName() + " approved your task: " + event.title(),
                        task, null, null, actor
                );
            }
        } catch (Exception e) {
            log.error("Failed to create notification for TaskApproved", e);
        }
    }

    @EventListener
    @Async
    @Transactional
    public void onTaskRejected(TaskEvents.TaskRejected event) {
        try {
            Employee assignee = employeeRepository.findById(event.assigneeId()).orElse(null);
            Employee actor = employeeRepository.findById(event.actorId()).orElse(null);
            Task task = taskRepository.findById(event.taskId()).orElse(null);

            if (assignee != null && actor != null && task != null) {
                notificationService.createNotification(
                        NotificationType.TASK_REJECTED,
                        assignee,
                        "Task returned for rework",
                        "Your task was returned: " + event.title(),
                        task, null, null, actor
                );
            }
        } catch (Exception e) {
            log.error("Failed to create notification for TaskRejected", e);
        }
    }

    @EventListener
    @Async
    @Transactional
    public void onTaskProposed(TaskEvents.TaskProposed event) {
        try {
            Task task = taskRepository.findById(event.taskId()).orElse(null);
            Employee proposer = employeeRepository.findById(event.proposerId()).orElse(null);
            if (task == null || proposer == null) return;

            // The proposal is a request to the team's leaders/managers to confirm it as a real task.
            List<TeamMembership> approvers = membershipRepository.findActiveByTeamIdAndRoleIn(
                    event.teamId(), List.of(Role.LEADER, Role.MANAGER));
            for (TeamMembership m : approvers) {
                if (m.getEmployee().getId().equals(event.proposerId())) continue; // don't notify the proposer
                notificationService.createNotification(
                        NotificationType.TASK_PROPOSED,
                        m.getEmployee(),
                        "New task to confirm",
                        event.proposerName() + " reported: " + event.title(),
                        task, null, null, proposer
                );
            }
        } catch (Exception e) {
            log.error("Failed to create notification for TaskProposed", e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onTeamJoinRequested(JoinRequestEvents.TeamJoinRequested event) {
        try {
            Employee requester = employeeRepository.findById(event.employeeId()).orElse(null);
            if (requester == null) return;
            List<TeamMembership> approvers = membershipRepository.findActiveByTeamIdAndRoleIn(
                    event.teamId(), List.of(Role.LEADER, Role.MANAGER));
            for (TeamMembership m : approvers) {
                notificationService.createNotification(
                        NotificationType.TEAM_JOIN_REQUESTED,
                        m.getEmployee(),
                        "New join request",
                        requester.getFullName() + " wants to join " + event.teamName() + ".",
                        null, null, null, requester
                );
            }
        } catch (Exception e) {
            log.error("Failed to create notification for TeamJoinRequested", e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onTeamJoinApproved(JoinRequestEvents.TeamJoinApproved event) {
        try {
            Employee requester = employeeRepository.findById(event.employeeId()).orElse(null);
            Employee actor = employeeRepository.findById(event.actorId()).orElse(null);
            if (requester != null && actor != null) {
                notificationService.createNotification(
                        NotificationType.TEAM_JOIN_APPROVED,
                        requester,
                        "Join request approved",
                        actor.getFullName() + " approved your request to join the team.",
                        null, null, null, actor
                );
            }
        } catch (Exception e) {
            log.error("Failed to create notification for TeamJoinApproved", e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onTeamJoinRejected(JoinRequestEvents.TeamJoinRejected event) {
        try {
            Employee requester = employeeRepository.findById(event.employeeId()).orElse(null);
            Employee actor = employeeRepository.findById(event.actorId()).orElse(null);
            if (requester != null && actor != null) {
                notificationService.createNotification(
                        NotificationType.TEAM_JOIN_REJECTED,
                        requester,
                        "Join request rejected",
                        actor.getFullName() + " declined your request to join the team.",
                        null, null, null, actor
                );
            }
        } catch (Exception e) {
            log.error("Failed to create notification for TeamJoinRejected", e);
        }
    }

    // AFTER_COMMIT: a plain @EventListener ran before the insert committed, so this could look up the
    // comment row, find nothing and silently create no notification at all. No method-level @Transactional
    // here on purpose — notificationService.createNotification opens its own.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onCommentAdded(CommentEvents.CommentAdded event) {
        try {
            Task task = taskRepository.findById(event.taskId()).orElse(null);
            TaskComment comment = commentRepository.findById(event.commentId()).orElse(null);
            Employee actor = employeeRepository.findById(event.authorId()).orElse(null);

            if (task == null || comment == null || actor == null) return;

            // Create notifications for mentioned users
            for (Long mentionedId : event.mentionedIds()) {
                Employee mentioned = employeeRepository.findById(mentionedId).orElse(null);
                if (mentioned != null) {
                    notificationService.createNotification(
                            NotificationType.MENTIONED,
                            mentioned,
                            "You were mentioned",
                            event.authorName() + " mentioned you in: " + event.taskTitle(),
                            task, comment, null, actor
                    );
                }
            }

            // …and for the task's own people (creator, assignee, reviewer), who hear about every comment
            // even without a mention. Disjoint from mentionedIds, so nobody gets both.
            for (Long participantId : event.participantIds()) {
                Employee participant = employeeRepository.findById(participantId).orElse(null);
                if (participant != null) {
                    notificationService.createNotification(
                            NotificationType.COMMENT_ADDED,
                            participant,
                            "New comment",
                            event.authorName() + " commented on: " + event.taskTitle(),
                            task, comment, null, actor
                    );
                }
            }
        } catch (Exception e) {
            log.error("Failed to create notification for CommentAdded", e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTicketAdminResponse(TicketEvents.TicketAdminResponse event) {
        try {
            Employee creator = employeeRepository.findById(event.recipientId()).orElse(null);
            if (creator == null) return;
            notificationService.createNotification(
                    NotificationType.TICKET_REPLY,
                    creator,
                    "Support reply",
                    event.adminName() + " replied to your ticket: " + event.subject(),
                    null, null, null, null
            );
        } catch (Exception e) {
            log.error("Failed to create notification for TicketAdminResponse", e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTicketStatusChanged(TicketEvents.TicketStatusChanged event) {
        try {
            Employee creator = employeeRepository.findById(event.creatorId()).orElse(null);
            if (creator == null) return;
            notificationService.createNotification(
                    NotificationType.TICKET_STATUS,
                    creator,
                    "Ticket updated",
                    "Your ticket is now " + event.newStatus() + ": " + event.subject(),
                    null, null, null, null
            );
        } catch (Exception e) {
            log.error("Failed to create notification for TicketStatusChanged", e);
        }
    }
}
