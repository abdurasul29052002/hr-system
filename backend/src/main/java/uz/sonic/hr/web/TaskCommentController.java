package uz.sonic.hr.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.sonic.hr.entity.TeamMembership;
import uz.sonic.hr.security.UserPrincipal;
import uz.sonic.hr.service.TaskCommentService;
import uz.sonic.hr.service.TeamService;
import uz.sonic.hr.web.dto.Dtos.CommentDto;
import uz.sonic.hr.web.dto.Dtos.CommentRequest;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TaskCommentController {

    private final TaskCommentService commentService;
    private final TeamService teamService;

    @PostMapping("/tasks/{taskId}/comments")
    public CommentDto addComment(@PathVariable Long taskId,
                                  @RequestParam("content") String content,
                                  @RequestParam(value = "files", required = false) List<MultipartFile> files,
                                  @AuthenticationPrincipal UserPrincipal principal,
                                  @RequestHeader(value = "X-Team-Id", required = false) Long teamId) {
        TeamMembership actor = teamService.requireMembership(principal.getEmployeeId(), teamId);
        CommentRequest request = new CommentRequest(content);
        return commentService.addComment(taskId, request, actor, files);
    }

    @GetMapping("/tasks/{taskId}/comments")
    public List<CommentDto> listComments(@PathVariable Long taskId,
                                          @AuthenticationPrincipal UserPrincipal principal,
                                          @RequestHeader(value = "X-Team-Id", required = false) Long teamId) {
        TeamMembership viewer = teamService.requireMembership(principal.getEmployeeId(), teamId);
        return commentService.listComments(taskId, viewer);
    }

    @GetMapping("/tasks/{taskId}/comments/count")
    public long getCommentCount(@PathVariable Long taskId,
                                @AuthenticationPrincipal UserPrincipal principal,
                                @RequestHeader(value = "X-Team-Id", required = false) Long teamId) {
        TeamMembership viewer = teamService.requireMembership(principal.getEmployeeId(), teamId);
        return commentService.getCommentCount(taskId, viewer);
    }

    @PutMapping("/comments/{commentId}")
    public CommentDto updateComment(@PathVariable Long commentId,
                                     @Valid @RequestBody CommentRequest request,
                                     @AuthenticationPrincipal UserPrincipal principal,
                                     @RequestHeader(value = "X-Team-Id", required = false) Long teamId) {
        TeamMembership actor = teamService.requireMembership(principal.getEmployeeId(), teamId);
        return commentService.updateComment(commentId, request, actor);
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable Long commentId,
                              @AuthenticationPrincipal UserPrincipal principal,
                              @RequestHeader(value = "X-Team-Id", required = false) Long teamId) {
        TeamMembership actor = teamService.requireMembership(principal.getEmployeeId(), teamId);
        commentService.deleteComment(commentId, actor);
    }
}
