package uz.sonic.hr.task;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.sonic.hr.task.TaskAttachment;
import uz.sonic.hr.team.TeamMembership;
import uz.sonic.hr.common.security.CurrentUser;
import uz.sonic.hr.task.TaskAttachmentService;
import uz.sonic.hr.team.TeamService;
import uz.sonic.hr.common.dto.Dtos.AttachmentDto;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TaskAttachmentController {

    private final TaskAttachmentService attachmentService;
    private final TeamService teamService;
    private final CurrentUser currentUser;

    @PostMapping("/tasks/{taskId}/attachments")
    public AttachmentDto uploadAttachment(@PathVariable Long taskId,
                                          @RequestParam("file") MultipartFile file,
                                          @RequestHeader(value = "X-Team-Id", required = false) Long teamId) {
        TeamMembership actor = teamService.requireMembership(currentUser.get().getId(), teamId);
        return attachmentService.uploadAttachment(taskId, file, actor);
    }

    @GetMapping("/tasks/{taskId}/attachments")
    public List<AttachmentDto> listAttachments(@PathVariable Long taskId,
                                               @RequestHeader(value = "X-Team-Id", required = false) Long teamId) {
        TeamMembership viewer = teamService.requireMembership(currentUser.get().getId(), teamId);
        return attachmentService.listAttachments(taskId, viewer);
    }

    @DeleteMapping("/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(@PathVariable Long attachmentId,
                                 @RequestHeader(value = "X-Team-Id", required = false) Long teamId) {
        TeamMembership actor = teamService.requireMembership(currentUser.get().getId(), teamId);
        attachmentService.deleteAttachment(attachmentId, actor);
    }

    @GetMapping("/attachments/{attachmentId}/url")
    public String getAttachmentUrl(@PathVariable Long attachmentId,
                                   @RequestHeader(value = "X-Team-Id", required = false) Long teamId) {
        TeamMembership viewer = teamService.requireMembership(currentUser.get().getId(), teamId);
        TaskAttachment attachment = attachmentService.getAttachment(attachmentId, viewer);
        return attachmentService.getAttachmentDownloadUrl(attachment);
    }
}
