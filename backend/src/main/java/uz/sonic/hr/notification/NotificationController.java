package uz.sonic.hr.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uz.sonic.hr.common.security.CurrentUser;
import uz.sonic.hr.notification.NotificationService;
import uz.sonic.hr.common.dto.Dtos.NotificationDto;
import uz.sonic.hr.common.dto.Dtos.UnreadCountDto;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUser currentUser;

    @GetMapping
    public List<NotificationDto> getNotifications(@RequestParam(defaultValue = "false") boolean unreadOnly) {
        return notificationService.getNotifications(currentUser.get().getId(), unreadOnly);
    }

    @GetMapping("/unread-count")
    public UnreadCountDto getUnreadCount() {
        return new UnreadCountDto(notificationService.getUnreadCount(currentUser.get().getId()));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id, currentUser.get().getId());
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllAsRead() {
        notificationService.markAllAsRead(currentUser.get().getId());
    }
}
