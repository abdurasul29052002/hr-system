package uz.sonic.hr.task;
import uz.sonic.hr.team.TeamService;
import uz.sonic.hr.common.storage.StorageService;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import uz.sonic.hr.common.enums.*;
import uz.sonic.hr.common.entity.*;
import uz.sonic.hr.employee.*;
import uz.sonic.hr.admin.*;
import uz.sonic.hr.team.*;
import uz.sonic.hr.task.*;
import uz.sonic.hr.notification.*;
import uz.sonic.hr.ticket.*;
import uz.sonic.hr.task.CommentAttachmentRepository;
import uz.sonic.hr.employee.EmployeeRepository;
import uz.sonic.hr.task.TaskCommentRepository;
import uz.sonic.hr.task.TaskRepository;
import uz.sonic.hr.team.TeamMembershipRepository;
import uz.sonic.hr.common.dto.Dtos.CommentDto;
import uz.sonic.hr.common.dto.Dtos.CommentRequest;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TaskCommentService {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@([a-zA-Z0-9_]+)");

    private final TaskCommentRepository commentRepository;
    private final TaskRepository taskRepository;
    private final EmployeeRepository employeeRepository;
    private final TeamMembershipRepository membershipRepository;
    private final CommentAttachmentRepository attachmentRepository;
    private final StorageService storage;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CommentDto addComment(Long taskId, CommentRequest request, TeamMembership actor, List<MultipartFile> files) {
        Task task = getTaskInTeam(taskId, actor);
        Employee author = actor.getEmployee();

        // Parse mentions from content
        Set<String> mentionedUsernames = extractMentions(request.content());
        Set<Employee> mentionedEmployees = resolveMentions(mentionedUsernames, task.getTeam());

        TaskComment comment = TaskComment.builder()
                .task(task)
                .author(author)
                .content(request.content())
                .build();

        // Create mention entities
        for (Employee mentioned : mentionedEmployees) {
            CommentMention mention = CommentMention.builder()
                    .comment(comment)
                    .mentionedEmployee(mentioned)
                    .build();
            comment.getMentions().add(mention);
        }

        comment = commentRepository.save(comment);

        // Upload attachments to S3
        if (files != null && !files.isEmpty()) {
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    String key = storage.upload(file, "comments/" + comment.getId());
                    CommentAttachment attachment = CommentAttachment.builder()
                            .comment(comment)
                            .fileName(file.getOriginalFilename())
                            .filePath(key)
                            .fileSize(file.getSize())
                            .mimeType(file.getContentType())
                            .build();
                    comment.getAttachments().add(attachment);
                    attachmentRepository.save(attachment);
                }
            }
        }

        publishCommentAdded(comment, task, author, request.content(), mentionedEmployees);

        return CommentDto.from(comment, storage);
    }

    @Transactional
    public CommentDto updateComment(Long commentId, CommentRequest request, TeamMembership actor) {
        TaskComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));

        // Check permissions: only author or manager can edit
        boolean isAuthor = comment.getAuthor().getId().equals(actor.getEmployee().getId());
        boolean isManager = TeamService.isManagerOrLeader(actor);

        if (!isAuthor && !isManager) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only edit your own comments");
        }

        // Check comment belongs to actor's team
        if (!comment.getTask().getTeam().getId().equals(actor.getTeam().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Comment not in your team");
        }

        comment.setContent(request.content());
        comment.setUpdatedAt(Instant.now());

        // Re-parse mentions
        comment.getMentions().clear();
        Set<String> mentionedUsernames = extractMentions(request.content());
        Set<Employee> mentionedEmployees = resolveMentions(mentionedUsernames, comment.getTask().getTeam());

        for (Employee mentioned : mentionedEmployees) {
            CommentMention mention = CommentMention.builder()
                    .comment(comment)
                    .mentionedEmployee(mentioned)
                    .build();
            comment.getMentions().add(mention);
        }

        return CommentDto.from(commentRepository.save(comment), storage);
    }

    @Transactional
    public void deleteComment(Long commentId, TeamMembership actor) {
        TaskComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));

        // Check permissions: only author or manager can delete
        boolean isAuthor = comment.getAuthor().getId().equals(actor.getEmployee().getId());
        boolean isManager = TeamService.isManagerOrLeader(actor);

        if (!isAuthor && !isManager) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own comments");
        }

        // Check comment belongs to actor's team
        if (!comment.getTask().getTeam().getId().equals(actor.getTeam().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Comment not in your team");
        }

        // Delete attachments from S3
        for (CommentAttachment attachment : comment.getAttachments()) {
            storage.delete(attachment.getFilePath());
        }

        commentRepository.delete(comment);
    }

    @Transactional(readOnly = true)
    public List<CommentDto> listComments(Long taskId, TeamMembership viewer) {
        Task task = getTaskInTeam(taskId, viewer);
        List<TaskComment> comments = commentRepository.findAllByTaskIdOrderByCreatedAtAsc(task.getId());
        return comments.stream().map(c -> CommentDto.from(c, storage)).toList();
    }

    @Transactional(readOnly = true)
    public long getCommentCount(Long taskId, TeamMembership viewer) {
        Task task = getTaskInTeam(taskId, viewer);
        return commentRepository.countByTaskId(task.getId());
    }

    private Task getTaskInTeam(Long taskId, TeamMembership viewer) {
        return taskRepository.findById(taskId)
                .filter(task -> task.getTeam().getId().equals(viewer.getTeam().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    /**
     * Extract all @username mentions from comment content.
     */
    private Set<String> extractMentions(String content) {
        Set<String> usernames = new HashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            usernames.add(matcher.group(1));
        }
        return usernames;
    }

    /**
     * Resolve usernames to Employee entities that are members of the team.
     */
    private Set<Employee> resolveMentions(Set<String> usernames, Team team) {
        if (usernames.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Employee> employees = new HashSet<>();
        for (String username : usernames) {
            employeeRepository.findByUsername(username).ifPresent(employee -> {
                // Check if employee is a member of this team
                boolean isMember = membershipRepository.findByEmployeeIdAndTeamId(employee.getId(), team.getId())
                        .isPresent();
                if (isMember) {
                    employees.add(employee);
                }
            });
        }
        return employees;
    }

    /**
     * Add comment from Telegram bot (reply to task notification).
     * Used when user replies to a Telegram message about a task.
     */
    @Transactional
    public TaskComment addCommentFromTelegram(Long taskId, Employee author, String content, Long telegramMessageId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));

        // Check if author is a member of the task's team
        boolean isMember = membershipRepository.findByEmployeeIdAndTeamId(author.getId(), task.getTeam().getId())
                .isPresent();
        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this team");
        }

        // Parse mentions
        Set<String> mentionedUsernames = extractMentions(content);
        Set<Employee> mentionedEmployees = resolveMentions(mentionedUsernames, task.getTeam());

        TaskComment comment = TaskComment.builder()
                .task(task)
                .author(author)
                .content(content)
                .telegramMessageId(telegramMessageId)
                .build();

        // Create mention entities
        for (Employee mentioned : mentionedEmployees) {
            CommentMention mention = CommentMention.builder()
                    .comment(comment)
                    .mentionedEmployee(mentioned)
                    .build();
            comment.getMentions().add(mention);
        }

        comment = commentRepository.save(comment);

        publishCommentAdded(comment, task, author, content, mentionedEmployees);

        return comment;
    }

    /**
     * Announces a new comment to everyone who should hear about it: the people explicitly @mentioned, plus
     * the task's own people — its creator, assignee and reviewer — who get told about every comment even
     * without a mention. The two sets are made disjoint here (and the author removed from both) so no one
     * is notified twice for the same comment, and the listeners can stay dumb.
     */
    private void publishCommentAdded(TaskComment comment, Task task, Employee author, String content,
                                     Set<Employee> mentionedEmployees) {
        Set<Long> mentionedIds = mentionedEmployees.stream()
                .map(Employee::getId)
                .filter(id -> !id.equals(author.getId())) // don't notify yourself
                .collect(java.util.stream.Collectors.toSet());

        Set<Long> participantIds = new java.util.HashSet<>();
        addParticipant(participantIds, task.getCreatedBy());
        addParticipant(participantIds, task.getAssignee());
        addParticipant(participantIds, task.getReviewer());
        participantIds.remove(author.getId());
        participantIds.removeAll(mentionedIds);
        // Only people who are still in the task's team. Removing someone from a team does NOT clear the
        // tasks they created or were assigned (createdBy is even NOT NULL, so a creator is permanent), and
        // unlinking their Telegram is not part of removal either — without this an ex-member would keep
        // receiving the full text of every comment on those tasks. @mentions are filtered the same way in
        // resolveMentions(); this keeps the two paths consistent.
        participantIds.removeIf(id ->
                membershipRepository.findByEmployeeIdAndTeamId(id, task.getTeam().getId()).isEmpty());

        if (mentionedIds.isEmpty() && participantIds.isEmpty()) {
            return;
        }
        eventPublisher.publishEvent(new CommentEvents.CommentAdded(
                comment.getId(), task.getId(), task.getTitle(), author.getId(),
                author.getFullName(), content, mentionedIds, participantIds));
    }

    private static void addParticipant(Set<Long> ids, Employee employee) {
        if (employee != null) {
            ids.add(employee.getId());
        }
    }
}
