package uz.sonic.hr.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.sonic.hr.entity.*;
import uz.sonic.hr.repo.EmployeeRepository;
import uz.sonic.hr.repo.TaskCommentRepository;
import uz.sonic.hr.repo.TaskRepository;
import uz.sonic.hr.repo.TeamInviteRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final EmployeeRepository employeeRepository;
    private final TaskRepository taskRepository;
    private final TaskCommentRepository commentRepository;
    private final TeamInviteRepository inviteRepository;

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
        } catch (Exception e) {
            log.error("Failed to create notification for CommentAdded", e);
        }
    }
}
