package uz.sonic.hr.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uz.sonic.hr.entity.*;
import uz.sonic.hr.repo.EmployeeRepository;
import uz.sonic.hr.repo.TaskCommentRepository;
import uz.sonic.hr.repo.TaskRepository;
import uz.sonic.hr.repo.TeamMembershipRepository;
import uz.sonic.hr.web.dto.Dtos.CommentDto;
import uz.sonic.hr.web.dto.Dtos.CommentRequest;

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
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CommentDto addComment(Long taskId, CommentRequest request, TeamMembership actor) {
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

        // Publish event for notifications
        if (!mentionedEmployees.isEmpty()) {
            Set<Long> mentionedIds = mentionedEmployees.stream()
                    .map(Employee::getId)
                    .filter(id -> !id.equals(author.getId())) // Don't notify yourself
                    .collect(java.util.stream.Collectors.toSet());

            if (!mentionedIds.isEmpty()) {
                eventPublisher.publishEvent(new CommentEvents.CommentAdded(
                        comment.getId(), taskId, task.getTitle(), author.getId(),
                        author.getFullName(), mentionedIds));
            }
        }

        return CommentDto.from(comment);
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

        return CommentDto.from(commentRepository.save(comment));
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

        commentRepository.delete(comment);
    }

    @Transactional(readOnly = true)
    public List<CommentDto> listComments(Long taskId, TeamMembership viewer) {
        Task task = getTaskInTeam(taskId, viewer);
        List<TaskComment> comments = commentRepository.findAllByTaskIdOrderByCreatedAtAsc(task.getId());
        return comments.stream().map(CommentDto::from).toList();
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
}
