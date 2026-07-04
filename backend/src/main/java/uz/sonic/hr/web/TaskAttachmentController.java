package uz.sonic.hr.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.sonic.hr.entity.TaskAttachment;
import uz.sonic.hr.entity.TeamMembership;
import uz.sonic.hr.security.UserPrincipal;
import uz.sonic.hr.service.TaskAttachmentService;
import uz.sonic.hr.service.TeamService;
import uz.sonic.hr.web.dto.Dtos.AttachmentDto;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TaskAttachmentController {

    private final TaskAttachmentService attachmentService;
    private final TeamService teamService;

    @PostMapping("/tasks/{taskId}/attachments")
    public AttachmentDto uploadAttachment(@PathVariable Long taskId,
                                          @RequestParam("file") MultipartFile file,
                                          @AuthenticationPrincipal UserPrincipal principal,
                                          @RequestHeader(value = "X-Team-Id", required = false) Long teamId) {
        TeamMembership actor = teamService.requireMembership(principal.getEmployeeId(), teamId);
        return attachmentService.uploadAttachment(taskId, file, actor);
    }

    @GetMapping("/tasks/{taskId}/attachments")
    public List<AttachmentDto> listAttachments(@PathVariable Long taskId,
                                               @AuthenticationPrincipal UserPrincipal principal,
                                               @RequestHeader(value = "X-Team-Id", required = false) Long teamId) {
        TeamMembership viewer = teamService.requireMembership(principal.getEmployeeId(), teamId);
        return attachmentService.listAttachments(taskId, viewer);
    }

    @DeleteMapping("/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(@PathVariable Long attachmentId,
                                 @AuthenticationPrincipal UserPrincipal principal,
                                 @RequestHeader(value = "X-Team-Id", required = false) Long teamId) {
        TeamMembership actor = teamService.requireMembership(principal.getEmployeeId(), teamId);
        attachmentService.deleteAttachment(attachmentId, actor);
    }

    @GetMapping("/attachments/{attachmentId}/url")
    public String getAttachmentUrl(@PathVariable Long attachmentId,
                                   @AuthenticationPrincipal UserPrincipal principal,
                                   @RequestHeader(value = "X-Team-Id", required = false) Long teamId) {
        TeamMembership viewer = teamService.requireMembership(principal.getEmployeeId(), teamId);
        TaskAttachment attachment = attachmentService.getAttachment(attachmentId, viewer);
        return attachmentService.getAttachmentDownloadUrl(attachment);
    }
}
