package uz.sonic.hr.task;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.sonic.hr.team.TeamMembership;
import uz.sonic.hr.common.security.CurrentUser;
import uz.sonic.hr.task.TaskCommentService;
import uz.sonic.hr.team.TeamService;
import uz.sonic.hr.common.dto.Dtos.CommentDto;
import uz.sonic.hr.common.dto.Dtos.CommentRequest;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TaskCommentController {

    private final TaskCommentService commentService;
    private final TeamService teamService;
    private final CurrentUser currentUser;

    @PostMapping("/tasks/{taskId}/comments")
    public CommentDto addComment(@PathVariable Long taskId,
                                  @RequestParam("content") String content,
                                  @RequestParam(value = "files", required = false) List<MultipartFile> files,
                                  @RequestHeader(value = "X-Team-Id", required = false) Long teamId) {
        TeamMembership actor = teamService.requireMembership(currentUser.get().getId(), teamId);
        CommentRequest request = new CommentRequest(content);
        return commentService.addComment(taskId, request, actor, files);
    }

    @GetMapping("/tasks/{taskId}/comments")
    public List<CommentDto> listComments(@PathVariable Long taskId,
                                          @RequestHeader(value = "X-Team-Id", required = false) Long teamId) {
        TeamMembership viewer = teamService.requireMembership(currentUser.get().getId(), teamId);
        return commentService.listComments(taskId, viewer);
    }

    @GetMapping("/tasks/{taskId}/comments/count")
    public long getCommentCount(@PathVariable Long taskId,
                                @RequestHeader(value = "X-Team-Id", required = false) Long teamId) {
        TeamMembership viewer = teamService.requireMembership(currentUser.get().getId(), teamId);
        return commentService.getCommentCount(taskId, viewer);
    }

    @PutMapping("/comments/{commentId}")
    public CommentDto updateComment(@PathVariable Long commentId,
                                     @Valid @RequestBody CommentRequest request,
                                     @RequestHeader(value = "X-Team-Id", required = false) Long teamId) {
        TeamMembership actor = teamService.requireMembership(currentUser.get().getId(), teamId);
        return commentService.updateComment(commentId, request, actor);
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable Long commentId,
                              @RequestHeader(value = "X-Team-Id", required = false) Long teamId) {
        TeamMembership actor = teamService.requireMembership(currentUser.get().getId(), teamId);
        commentService.deleteComment(commentId, actor);
    }
}
