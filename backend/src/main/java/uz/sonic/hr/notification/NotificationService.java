package uz.sonic.hr.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uz.sonic.hr.common.enums.*;
import uz.sonic.hr.common.entity.*;
import uz.sonic.hr.employee.*;
import uz.sonic.hr.admin.*;
import uz.sonic.hr.team.*;
import uz.sonic.hr.task.*;
import uz.sonic.hr.notification.*;
import uz.sonic.hr.ticket.*;
import uz.sonic.hr.notification.NotificationRepository;
import uz.sonic.hr.common.dto.Dtos.NotificationDto;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public void createNotification(NotificationType type, Employee recipient, String title, String message,
                                    Task relatedTask, TaskComment relatedComment, TeamInvite relatedInvite,
                                    Employee actor) {
        Notification notification = Notification.builder()
                .employee(recipient)
                .type(type)
                .title(title)
                .message(message)
                .relatedTask(relatedTask)
                .relatedComment(relatedComment)
                .relatedInvite(relatedInvite)
                .actor(actor)
                .build();

        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getNotifications(Long employeeId, boolean unreadOnly) {
        List<Notification> notifications;
        if (unreadOnly) {
            notifications = notificationRepository.findAllByEmployeeIdAndIsReadOrderByCreatedAtDesc(employeeId, false);
        } else {
            notifications = notificationRepository.findAllByEmployeeIdOrderByCreatedAtDesc(employeeId);
        }
        return notifications.stream().map(NotificationDto::from).toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long employeeId) {
        return notificationRepository.countByEmployeeIdAndIsRead(employeeId, false);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long employeeId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));

        // Security check: only owner can mark as read
        if (!notification.getEmployee().getId().equals(employeeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your notification");
        }

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(Long employeeId) {
        notificationRepository.markAllAsReadByEmployeeId(employeeId);
    }
}
