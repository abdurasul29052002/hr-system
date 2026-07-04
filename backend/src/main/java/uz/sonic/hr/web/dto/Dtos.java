package uz.sonic.hr.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import uz.sonic.hr.entity.*;

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
                    e.isAdmin(), e.getTelegramChatId() != null, e.getTelegramLinkCode(), e.isActive(),
                    memberships.stream().map(MyTeam::from).toList());
        }
    }

    /** A member row of the currently selected team. */
    public record MemberDto(Long employeeId, String fullName, String username, String phone, String position,
                            Role role, boolean telegramLinked, String telegramLinkCode, Instant joinedAt) {

        public static MemberDto from(TeamMembership m) {
            Employee e = m.getEmployee();
            return new MemberDto(e.getId(), e.getFullName(), e.getUsername(), e.getPhone(), m.getPosition(),
                    m.getRole(), e.getTelegramChatId() != null, e.getTelegramLinkCode(), m.getJoinedAt());
        }
    }

    /** Create a brand-new user inside the current team. */
    public record NewMemberRequest(@NotBlank String fullName, @NotBlank String username, @NotBlank String password,
                                   String phone, String position, Role role, Language language) {
    }

    /** Add an already-registered user to the current team by username. */
    public record AddMemberRequest(@NotBlank String username, Role role, String position) {
    }

    /** Update a member of the current team (role change is leader-only). */
    public record UpdateMemberRequest(Role role, String position) {
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

    public record TagDto(Long id, String name, String color) {

        public static TagDto from(Tag tag) {
            return new TagDto(tag.getId(), tag.getName(), tag.getColor());
        }
    }

    public record TagRequest(@NotBlank String name, String color) {
    }

    public record TaskDto(Long id, String title, String description, TaskPriority priority, TaskStatus status,
                          Long createdById, String createdByName, Long assigneeId, String assigneeName,
                          List<TagDto> tags, LocalDate deadline, Instant createdAt, Instant takenAt,
                          Instant completedAt) {

        public static TaskDto from(Task t) {
            return new TaskDto(t.getId(), t.getTitle(), t.getDescription(), t.getPriority(), t.getStatus(),
                    t.getCreatedBy().getId(), t.getCreatedBy().getFullName(),
                    t.getAssignee() != null ? t.getAssignee().getId() : null,
                    t.getAssignee() != null ? t.getAssignee().getFullName() : null,
                    t.getTags().stream().map(TagDto::from).sorted(Comparator.comparing(TagDto::name)).toList(),
                    t.getDeadline(), t.getCreatedAt(), t.getTakenAt(), t.getCompletedAt());
        }
    }

    public record TaskRequest(@NotBlank String title, String description, TaskPriority priority,
                              LocalDate deadline, List<Long> tagIds, Long assigneeId) {
    }

    public record AssignRequest(@NotNull Long employeeId) {
    }

    public record TeamAdminDto(Long id, String name, long memberCount, Instant createdAt) {
    }

    public record EmployeeStats(Long employeeId, String fullName, long taken, long completed, long inProgress,
                                long overdue, Double avgCompletionHours) {
    }

    public record MonthlyStats(int year, int month, long totalCreated, long totalCompleted, long totalOpen,
                               long totalInProgress, long totalTesting, List<EmployeeStats> perEmployee) {
    }

    public record ChangePasswordRequest(@NotBlank String oldPassword, @NotBlank String newPassword) {
    }

    public record UpdateLanguageRequest(@NotNull Language language) {
    }
}
