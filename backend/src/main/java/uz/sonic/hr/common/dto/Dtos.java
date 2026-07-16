package uz.sonic.hr.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.sonic.hr.common.enums.*;
import uz.sonic.hr.common.entity.*;
import uz.sonic.hr.employee.*;
import uz.sonic.hr.admin.*;
import uz.sonic.hr.team.*;
import uz.sonic.hr.task.*;
import uz.sonic.hr.notification.*;
import uz.sonic.hr.ticket.*;
import uz.sonic.hr.common.storage.StorageService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public final class Dtos {

    private Dtos() {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record RegisterRequest(@NotBlank String fullName, @NotBlank String username, @NotBlank String password,
                                  String phone, Language language) {
    }

    public record LoginResponse(String token, EmployeeDto employee) {
    }

    public record CreateTeamRequest(@NotBlank String name) {
    }

    /** One of my team memberships, embedded in EmployeeDto. */
    public record MyTeam(Long teamId, String teamName, Role role, String position) {

        public static MyTeam from(TeamMembership m) {
            return new MyTeam(m.getTeam().getId(), m.getTeam().getName(), m.getRole(), m.getPosition());
        }
    }

    public record EmployeeDto(Long id, String fullName, String username, String phone, Language language,
                              boolean admin, boolean telegramLinked, String telegramLinkCode, boolean active,
                              List<MyTeam> memberships) {

        public static EmployeeDto from(Employee e, List<TeamMembership> memberships) {
            return new EmployeeDto(e.getId(), e.getFullName(), e.getUsername(), e.getPhone(), e.getLanguage(),
                    false, e.getTelegramChatId() != null, e.getTelegramLinkCode(), e.isActive(),
                    memberships.stream().map(MyTeam::from).toList());
        }

        public static EmployeeDto fromAdmin(Admin a) {
            return new EmployeeDto(a.getId(), a.getFullName(), a.getUsername(), null, a.getLanguage(),
                    true, false, null, a.isActive(), List.of());
        }
    }

    public record MemberLabelDto(Long id, String name, String color) {

        public static MemberLabelDto from(MemberLabel label) {
            return new MemberLabelDto(label.getId(), label.getName(), label.getColor());
        }
    }

    public record MemberLabelRequest(@NotBlank String name, String color) {
    }

    /** A member row of the currently selected team. */
    public record MemberDto(Long employeeId, String fullName, String username, String phone, String position,
                            Role role, List<MemberLabelDto> labels, boolean telegramLinked, String telegramLinkCode,
                            Instant joinedAt) {

        public static MemberDto from(TeamMembership m) {
            Employee e = m.getEmployee();
            return new MemberDto(e.getId(), e.getFullName(), e.getUsername(), e.getPhone(), m.getPosition(),
                    m.getRole(),
                    m.getLabels().stream().map(MemberLabelDto::from)
                            .sorted(Comparator.comparing(MemberLabelDto::name)).toList(),
                    e.getTelegramChatId() != null, e.getTelegramLinkCode(), m.getJoinedAt());
        }
    }

    /**
     * Minimal team-member info for @mention autocomplete in comments — safe for ANY team member to
     * read (unlike {@link MemberDto} it omits phone / Telegram link code / role).
     */
    public record MentionMemberDto(Long employeeId, String fullName, String username) {
        public static MentionMemberDto from(TeamMembership m) {
            Employee e = m.getEmployee();
            return new MentionMemberDto(e.getId(), e.getFullName(), e.getUsername());
        }
    }

    /** Create a brand-new user inside the current team. */
    public record NewMemberRequest(@NotBlank String fullName, @NotBlank String username, @NotBlank String password,
                                   String phone, String position, Role role, Language language, List<Long> labelIds) {
    }

    /** Add an already-registered user to the current team by username. */
    public record AddMemberRequest(@NotBlank String username, Role role, String position, List<Long> labelIds) {
    }

    /** Update a member of the current team (role change is leader-only). */
    public record UpdateMemberRequest(Role role, String position, List<Long> labelIds) {
    }

    public record InviteDto(Long id, String token, Role role, Instant createdAt, Instant expiresAt,
                            String createdByName) {

        public static InviteDto from(TeamInvite invite) {
            return new InviteDto(invite.getId(), invite.getToken(), invite.getRole(), invite.getCreatedAt(),
                    invite.getExpiresAt(), invite.getCreatedBy().getFullName());
        }
    }

    public record CreateInviteRequest(Role role) {
    }

    /** Shown on the join page before accepting. */
    public record InviteInfo(String teamName, Role role, boolean alreadyMember) {
    }

    /** Public team search result shown before a team-less user asks to join. */
    public record TeamSearchDto(Long id, String name, long memberCount, Instant createdAt) {
    }

    public record TeamJoinRequestDto(Long id, Long teamId, String teamName,
                                     Long employeeId, String employeeName, String employeeUsername,
                                     JoinRequestStatus status, Instant createdAt) {

        public static TeamJoinRequestDto from(TeamJoinRequest request) {
            return new TeamJoinRequestDto(
                    request.getId(),
                    request.getTeam().getId(),
                    request.getTeam().getName(),
                    request.getEmployee().getId(),
                    request.getEmployee().getFullName(),
                    request.getEmployee().getUsername(),
                    request.getStatus(),
                    request.getCreatedAt()
            );
        }
    }

    public record TagDto(Long id, String name, String color) {

        public static TagDto from(Tag tag) {
            return new TagDto(tag.getId(), tag.getName(), tag.getColor());
        }
    }

    public record TagRequest(@NotBlank String name, String color) {
    }

    public record TaskDto(Long id, String title, String description, TaskPriority priority, TaskStatus status,
                          Long createdById, String createdByName, Long assigneeId, String assigneeName,
                          Long reviewerId, String reviewerName,
                          List<TagDto> tags, LocalDate deadline, Instant createdAt, Instant takenAt,
                          Instant submittedAt, Instant completedAt) {

        public static TaskDto from(Task t) {
            return new TaskDto(t.getId(), t.getTitle(), t.getDescription(), t.getPriority(), t.getStatus(),
                    t.getCreatedBy().getId(), t.getCreatedBy().getFullName(),
                    t.getAssignee() != null ? t.getAssignee().getId() : null,
                    t.getAssignee() != null ? t.getAssignee().getFullName() : null,
                    t.getReviewer() != null ? t.getReviewer().getId() : null,
                    t.getReviewer() != null ? t.getReviewer().getFullName() : null,
                    t.getTags().stream().map(TagDto::from).sorted(Comparator.comparing(TagDto::name)).toList(),
                    t.getDeadline(), t.getCreatedAt(), t.getTakenAt(), t.getSubmittedAt(), t.getCompletedAt());
        }
    }

    /**
     * The four lifecycle timestamps let a task be entered with real (often past) times — to record work
     * that already happened. All optional; the task's status is derived from the furthest one supplied:
     * createdAt→(OPEN), takenAt→IN_PROGRESS, submittedAt→TESTING, completedAt→DONE. They must be in
     * non-decreasing order, and each later one requires {@code takenAt}. {@code createdAt} defaults to now.
     */
    public record TaskRequest(@NotBlank @Size(max = 120) String title, String description, TaskPriority priority,
                              LocalDate deadline, List<Long> tagIds, Long assigneeId, Long reviewerId,
                              Instant createdAt, Instant takenAt, Instant submittedAt, Instant completedAt) {
    }

    public record AssignRequest(@NotNull Long employeeId) {
    }

    public record TeamAdminDto(Long id, String name, long memberCount, Instant createdAt) {
    }

    /** One of a member's tasks in the month, for the graphical status-distribution bar. */
    public record TaskBriefDto(Long id, String title, TaskStatus status, boolean overdue) {
    }

    public record EmployeeStats(Long employeeId, String fullName, long taken, long completed, long inProgress,
                                long testing, long cancelled, long overdue, long onTime, long reviewed,
                                Double avgCompletionHours, List<TaskBriefDto> tasks) {
    }

    /** One slice of the overall task-distribution bar (like a phone-storage breakdown). */
    public record StatusSlice(String category, long count) {
    }

    public record MonthlyStats(int year, int month, long totalCreated, long totalCompleted, long totalOpen,
                               long totalInProgress, long totalTesting, long totalCancelled, long totalOverdue,
                               String employeeOfMonth, List<EmployeeStats> perEmployee,
                               List<StatusSlice> distribution) {
    }

    /** A task a member is actively working on right now. */
    public record ActiveTaskDto(Long id, String title, TaskStatus status, TaskPriority priority,
                                LocalDate deadline, Instant takenAt) {

        public static ActiveTaskDto from(Task t) {
            return new ActiveTaskDto(t.getId(), t.getTitle(), t.getStatus(), t.getPriority(),
                    t.getDeadline(), t.getTakenAt());
        }
    }

    /** What one member is currently working on. */
    public record MemberActivityDto(Long employeeId, String fullName, String position,
                                    List<MemberLabelDto> labels, List<ActiveTaskDto> activeTasks) {
    }

    /** One task on the monthly timeline (Gantt): its taken → completed span within the month. */
    public record TimelineTaskDto(Long id, String title, String assigneeName, TaskStatus status, TaskPriority priority,
                                  Instant createdAt, Instant takenAt, Instant submittedAt, Instant completedAt,
                                  LocalDate deadline) {

        public static TimelineTaskDto from(Task t) {
            return new TimelineTaskDto(t.getId(), t.getTitle(),
                    t.getAssignee() != null ? t.getAssignee().getFullName() : null,
                    t.getStatus(), t.getPriority(),
                    t.getCreatedAt(), t.getTakenAt(), t.getSubmittedAt(), t.getCompletedAt(), t.getDeadline());
        }
    }

    public record ChangePasswordRequest(@NotBlank String oldPassword, @NotBlank String newPassword) {
    }

    /** Self-service account deletion — the current password re-confirms the destructive action. */
    public record DeleteAccountRequest(@NotBlank String password) {
    }

    public record UpdateLanguageRequest(@NotNull Language language) {
    }

    // Task Comments
    public record CommentDto(Long id, Long taskId, Long authorId, String authorName, String content,
                             Instant createdAt, Instant updatedAt, boolean viaTelegram,
                             List<Long> mentionedEmployeeIds, List<CommentAttachmentDto> attachments) {

        public static CommentDto from(TaskComment comment, StorageService storage) {
            return new CommentDto(
                    comment.getId(),
                    comment.getTask().getId(),
                    comment.getAuthor().getId(),
                    comment.getAuthor().getFullName(),
                    comment.getContent(),
                    comment.getCreatedAt(),
                    comment.getUpdatedAt(),
                    comment.getTelegramMessageId() != null, // this comment was posted by replying on Telegram
                    comment.getMentions().stream()
                            .map(m -> m.getMentionedEmployee().getId())
                            .toList(),
                    comment.getAttachments().stream()
                            .map(a -> CommentAttachmentDto.from(a, storage))
                            .toList()
            );
        }
    }

    public record CommentAttachmentDto(Long id, String fileName, Long fileSize, String mimeType,
                                       Instant uploadedAt, String downloadUrl) {

        public static CommentAttachmentDto from(CommentAttachment attachment, StorageService storage) {
            return new CommentAttachmentDto(
                    attachment.getId(),
                    attachment.getFileName(),
                    attachment.getFileSize(),
                    attachment.getMimeType(),
                    attachment.getUploadedAt(),
                    storage.publicUrl(attachment.getFilePath())
            );
        }
    }

    public record CommentRequest(@NotBlank String content) {
    }

    // Task Attachments
    public record AttachmentDto(Long id, Long taskId, String fileName, Long fileSize,
                                String mimeType, String uploadedByName, Instant uploadedAt,
                                String downloadUrl) {

        public static AttachmentDto from(TaskAttachment attachment, String downloadUrl) {
            return new AttachmentDto(
                    attachment.getId(),
                    attachment.getTask().getId(),
                    attachment.getFileName(),
                    attachment.getFileSize(),
                    attachment.getMimeType(),
                    attachment.getUploadedBy().getFullName(),
                    attachment.getUploadedAt(),
                    downloadUrl
            );
        }
    }

    // Notifications
    public record NotificationDto(Long id, NotificationType type, String title, String message,
                                  Long relatedTaskId, Long relatedCommentId, Long relatedInviteId,
                                  String actorName, boolean isRead, Instant createdAt) {

        public static NotificationDto from(Notification notification) {
            return new NotificationDto(
                    notification.getId(),
                    notification.getType(),
                    notification.getTitle(),
                    notification.getMessage(),
                    notification.getRelatedTask() != null ? notification.getRelatedTask().getId() : null,
                    notification.getRelatedComment() != null ? notification.getRelatedComment().getId() : null,
                    notification.getRelatedInvite() != null ? notification.getRelatedInvite().getId() : null,
                    notification.getActor() != null ? notification.getActor().getFullName() : null,
                    notification.isRead(),
                    notification.getCreatedAt()
            );
        }
    }

    public record UnreadCountDto(long count) {
    }

    // Tickets
    public record CreateTicketRequest(@NotBlank String subject, @NotBlank String description,
                                      @NotNull TicketType type, TicketPriority priority) {
    }

    public record TicketDto(Long id, Long creatorId, String creatorName, String subject, String description,
                            TicketType type, TicketPriority priority, TicketStatus status,
                            Instant createdAt, Instant updatedAt, Instant resolvedAt, String resolvedByName,
                            int messageCount) {

        public static TicketDto from(Ticket ticket) {
            return new TicketDto(
                    ticket.getId(),
                    ticket.getCreator().getId(),
                    ticket.getCreator().getFullName(),
                    ticket.getSubject(),
                    ticket.getDescription(),
                    ticket.getType(),
                    ticket.getPriority(),
                    ticket.getStatus(),
                    ticket.getCreatedAt(),
                    ticket.getUpdatedAt(),
                    ticket.getResolvedAt(),
                    ticket.getResolvedBy() != null ? ticket.getResolvedBy().getFullName() : null,
                    ticket.getMessages().size()
            );
        }
    }

    public record TicketDetailDto(Long id, Long creatorId, String creatorName, String subject, String description,
                                  TicketType type, TicketPriority priority, TicketStatus status,
                                  Instant createdAt, Instant updatedAt, Instant resolvedAt, String resolvedByName,
                                  List<TicketMessageDto> messages) {

        public static TicketDetailDto from(Ticket ticket) {
            return new TicketDetailDto(
                    ticket.getId(),
                    ticket.getCreator().getId(),
                    ticket.getCreator().getFullName(),
                    ticket.getSubject(),
                    ticket.getDescription(),
                    ticket.getType(),
                    ticket.getPriority(),
                    ticket.getStatus(),
                    ticket.getCreatedAt(),
                    ticket.getUpdatedAt(),
                    ticket.getResolvedAt(),
                    ticket.getResolvedBy() != null ? ticket.getResolvedBy().getFullName() : null,
                    ticket.getMessages().stream().map(TicketMessageDto::from).toList()
            );
        }
    }

    public record TicketMessageDto(Long id, Long senderId, String senderName, String message,
                                   boolean isAdminResponse, Instant createdAt) {

        public static TicketMessageDto from(TicketMessage msg) {
            return new TicketMessageDto(
                    msg.getId(),
                    msg.senderId(),
                    msg.senderName(),
                    msg.getMessage(),
                    msg.isAdminResponse(),
                    msg.getCreatedAt()
            );
        }
    }

    public record AddMessageRequest(@NotBlank String message) {
    }

    public record UpdateTicketStatusRequest(@NotNull TicketStatus status) {
    }

    public record UpdateTicketPriorityRequest(@NotNull TicketPriority priority) {
    }

    public record TicketStatsDto(long openCount, long inProgressCount, long resolvedCount, long totalCount) {
    }
}

