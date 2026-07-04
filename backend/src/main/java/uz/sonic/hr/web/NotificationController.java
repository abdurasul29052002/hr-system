package uz.sonic.hr.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.sonic.hr.security.UserPrincipal;
import uz.sonic.hr.service.NotificationService;
import uz.sonic.hr.web.dto.Dtos.NotificationDto;
import uz.sonic.hr.web.dto.Dtos.UnreadCountDto;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationDto> getNotifications(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return notificationService.getNotifications(principal.getEmployeeId(), unreadOnly);
    }

    @GetMapping("/unread-count")
    public UnreadCountDto getUnreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        long count = notificationService.getUnreadCount(principal.getEmployeeId());
        return new UnreadCountDto(count);
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsRead(@PathVariable Long id,
                          @AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markAsRead(id, principal.getEmployeeId());
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllAsRead(@AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markAllAsRead(principal.getEmployeeId());
    }
}
