package uz.sonic.hr.task;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.sonic.hr.common.enums.TaskStatus;
import uz.sonic.hr.team.MemberLabel;
import uz.sonic.hr.team.TeamMembership;
import uz.sonic.hr.team.TeamMembershipRepository;
import uz.sonic.hr.common.dto.Dtos.*;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final TaskRepository taskRepository;
    private final TeamMembershipRepository membershipRepository;

    /**
     * Fixed reporting zone (Uzbekistan, UTC+5, no DST). Using a fixed zone instead of the JVM default
     * keeps month bucketing deployment-independent and makes the backend agree with the web timeline,
     * which positions bars in the same zone.
     */
    private static final ZoneId ZONE = ZoneId.of("Asia/Tashkent");

    @Transactional(readOnly = true)
    public MonthlyStats monthly(TeamMembership viewer, int year, int month) {
        Long teamId = viewer.getTeam().getId();
        ZoneId zone = ZONE;
        Instant from = YearMonth.of(year, month).atDay(1).atStartOfDay(zone).toInstant();
        Instant to = YearMonth.of(year, month).plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        List<Task> tasks = taskRepository.findAllCreatedBetween(teamId, from, to);
        // Tasks whose review was FINISHED this month (approved → DONE, by completedAt). Powers both
        // "employee of the month" and the reviewed counts — a review is credited to the month it was
        // completed in, independent of when the task was created (consistent windows).
        List<Task> completedThisMonth = taskRepository.findAllCompletedBetween(teamId, from, to);

        long created = tasks.size();
        long completed = count(tasks, TaskStatus.DONE);
        long open = count(tasks, TaskStatus.OPEN);
        long inProgress = count(tasks, TaskStatus.IN_PROGRESS);
        long testing = count(tasks, TaskStatus.TESTING);
        long cancelled = count(tasks, TaskStatus.CANCELLED);
        long totalOverdue = tasks.stream().filter(t -> isOverdue(t, zone)).count();

        Map<Long, List<Task>> byAssignee = new LinkedHashMap<>();
        for (Task task : tasks) {
            if (task.getAssignee() != null) {
                byAssignee.computeIfAbsent(task.getAssignee().getId(), k -> new ArrayList<>()).add(task);
            }
        }

        // How many tasks each person reviewed (approved) this month — powers "who reviewed it" and the
        // reviewed count. Keyed off completion (not creation) so a review counts in the month it happened.
        Map<Long, Long> reviewedCount = new HashMap<>();
        Map<Long, String> reviewerName = new HashMap<>();
        for (Task task : completedThisMonth) {
            if (task.getReviewer() != null) {
                reviewedCount.merge(task.getReviewer().getId(), 1L, Long::sum);
                reviewerName.putIfAbsent(task.getReviewer().getId(), task.getReviewer().getFullName());
            }
        }

        List<EmployeeStats> perEmployee = new ArrayList<>();
        for (List<Task> employeeTasks : byAssignee.values()) {
            Task first = employeeTasks.getFirst();
            long taken = employeeTasks.size();
            long done = count(employeeTasks, TaskStatus.DONE);
            long working = count(employeeTasks, TaskStatus.IN_PROGRESS);
            long inReview = count(employeeTasks, TaskStatus.TESTING);
            long cancelledForEmp = count(employeeTasks, TaskStatus.CANCELLED);
            long reviewed = reviewedCount.getOrDefault(first.getAssignee().getId(), 0L);
            long overdue = employeeTasks.stream().filter(t -> isOverdue(t, zone)).count();
            // On time = the assignee delivered (submitted) by the work deadline — consistent with isOverdue.
            long onTime = employeeTasks.stream()
                    .filter(t -> t.getStatus() == TaskStatus.DONE && !isOverdue(t, zone))
                    .count();
            OptionalDouble avgHours = employeeTasks.stream()
                    .filter(t -> t.getTakenAt() != null
                            && (t.getSubmittedAt() != null || t.getCompletedAt() != null))
                    .mapToDouble(t -> Duration.between(t.getTakenAt(),
                            t.getSubmittedAt() != null ? t.getSubmittedAt() : t.getCompletedAt())
                            .toMinutes() / 60.0)
                    .average();
            List<TaskBriefDto> briefs = employeeTasks.stream()
                    .map(t -> new TaskBriefDto(t.getId(), t.getTitle(), t.getStatus(), isOverdue(t, zone)))
                    .toList();
            perEmployee.add(new EmployeeStats(
                    first.getAssignee().getId(),
                    first.getAssignee().getFullName(),
                    taken, done, working, inReview, cancelledForEmp, overdue, onTime, reviewed,
                    avgHours.isPresent() ? Math.round(avgHours.getAsDouble() * 10) / 10.0 : null,
                    briefs));
        }
        // Reviewers who took NO task themselves this month would otherwise be invisible here — their review
        // work is real work. Add a row for each (zeroed assignee metrics, only their reviewed count) so the
        // performance section credits pure reviewers too.
        for (Map.Entry<Long, Long> e : reviewedCount.entrySet()) {
            if (!byAssignee.containsKey(e.getKey())) {
                perEmployee.add(new EmployeeStats(e.getKey(), reviewerName.get(e.getKey()),
                        0, 0, 0, 0, 0, 0, 0, e.getValue(), null, List.of()));
            }
        }
        perEmployee.sort(Comparator.comparingLong(EmployeeStats::completed).reversed()
                .thenComparing(Comparator.comparingLong(EmployeeStats::reviewed).reversed())
                .thenComparing(Comparator.comparingLong(EmployeeStats::onTime).reversed()));

        // Employee of the month: whoever COMPLETED the most tasks THIS month (by completedAt), independent
        // of when each task was created — so it reflects work actually finished this month (and back-dated
        // historical entries count toward the month they were completed in, not viewed in).
        Map<Long, Long> doneByEmp = new HashMap<>();
        Map<Long, String> doneEmpName = new LinkedHashMap<>();
        for (Task t : completedThisMonth) {
            if (t.getAssignee() != null) {
                doneByEmp.merge(t.getAssignee().getId(), 1L, Long::sum);
                doneEmpName.putIfAbsent(t.getAssignee().getId(), t.getAssignee().getFullName());
            }
        }
        String employeeOfMonth = doneByEmp.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> doneEmpName.get(e.getKey()))
                .orElse(null);

        // Overall distribution of every task in the month, one slice per category (overdue carved out
        // of its status). Slices partition totalCreated — the phone-storage-style breakdown bar.
        Map<String, Long> distMap = new HashMap<>();
        for (Task t : tasks) {
            String cat = isOverdue(t, zone) ? "OVERDUE" : t.getStatus().name();
            distMap.merge(cat, 1L, Long::sum);
        }
        List<StatusSlice> distribution = new ArrayList<>();
        for (String cat : List.of("DONE", "OVERDUE", "TESTING", "IN_PROGRESS", "OPEN", "CANCELLED")) {
            long c = distMap.getOrDefault(cat, 0L);
            if (c > 0) {
                distribution.add(new StatusSlice(cat, c));
            }
        }

        return new MonthlyStats(year, month, created, completed, open, inProgress, testing, cancelled,
                totalOverdue, employeeOfMonth, perEmployee, distribution);
    }

    /** Live snapshot: every team member and the tasks they are actively working on. */
    @Transactional(readOnly = true)
    public List<MemberActivityDto> current(TeamMembership viewer) {
        Long teamId = viewer.getTeam().getId();
        List<Task> active = taskRepository.findActiveWithParticipants(
                teamId, List.of(TaskStatus.IN_PROGRESS, TaskStatus.TESTING));
        Map<Long, List<ActiveTaskDto>> byMember = new HashMap<>();
        for (Task t : active) {
            Long assigneeId = t.getAssignee() != null ? t.getAssignee().getId() : null;
            if (assigneeId != null) {
                // The assignee is occupied by it (IN_PROGRESS = doing, TESTING = standing by to fix bugs).
                byMember.computeIfAbsent(assigneeId, k -> new ArrayList<>()).add(ActiveTaskDto.from(t, false));
            }
            // A task in review also occupies its reviewer — surface it under them as "reviewing" (unless the
            // reviewer is the assignee, who already has it, to avoid listing the same task twice for them).
            if (t.getStatus() == TaskStatus.TESTING && t.getReviewer() != null
                    && !t.getReviewer().getId().equals(assigneeId)) {
                byMember.computeIfAbsent(t.getReviewer().getId(), k -> new ArrayList<>()).add(ActiveTaskDto.from(t, true));
            }
        }
        List<TeamMembership> memberships = membershipRepository.findAllByTeamIdWithEmployee(teamId);
        List<MemberActivityDto> result = new ArrayList<>();
        for (TeamMembership m : memberships) {
            List<ActiveTaskDto> tasks = byMember.getOrDefault(m.getEmployee().getId(), List.of());
            List<MemberLabelDto> labels = m.getLabels().stream()
                    .sorted(Comparator.comparing(MemberLabel::getName))
                    .map(MemberLabelDto::from).toList();
            result.add(new MemberActivityDto(m.getEmployee().getId(), m.getEmployee().getFullName(),
                    m.getPosition(), labels, tasks));
        }
        // busiest first
        result.sort(Comparator.comparingInt((MemberActivityDto a) -> a.activeTasks().size()).reversed());
        return result;
    }

    /** Every task whose taken → completed span overlaps the given month, for the Gantt timeline. */
    @Transactional(readOnly = true)
    public List<TimelineTaskDto> timeline(TeamMembership viewer, int year, int month) {
        Long teamId = viewer.getTeam().getId();
        ZoneId zone = ZONE;
        Instant from = YearMonth.of(year, month).atDay(1).atStartOfDay(zone).toInstant();
        Instant to = YearMonth.of(year, month).plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        return taskRepository.findAllOverlapping(teamId, from, to).stream()
                .map(TimelineTaskDto::from)
                .toList();
    }

    private static long count(List<Task> tasks, TaskStatus status) {
        return tasks.stream().filter(t -> t.getStatus() == status).count();
    }

    /**
     * Whether the ASSIGNEE missed the work deadline. Their obligation ends when they SUBMIT for review, so
     * this measures against submittedAt (their delivery), not completedAt — time a task spends waiting for a
     * reviewer is the reviewer's, not theirs. Falls back to completedAt (a leader completing directly), else
     * now (still in progress → not delivered). OPEN/CANCELLED/no-deadline are never overdue.
     */
    private static boolean isOverdue(Task t, ZoneId zone) {
        if (t.getDeadline() == null || t.getStatus() == TaskStatus.CANCELLED || t.getStatus() == TaskStatus.OPEN) {
            return false;
        }
        Instant delivered = t.getSubmittedAt() != null ? t.getSubmittedAt() : t.getCompletedAt();
        LocalDate deliveredDate = delivered != null ? delivered.atZone(zone).toLocalDate() : LocalDate.now(zone);
        return deliveredDate.isAfter(t.getDeadline());
    }
}
