package uz.sonic.hr.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.sonic.hr.entity.Task;
import uz.sonic.hr.entity.TaskStatus;
import uz.sonic.hr.entity.TeamMembership;
import uz.sonic.hr.repo.TaskRepository;
import uz.sonic.hr.web.dto.Dtos.EmployeeStats;
import uz.sonic.hr.web.dto.Dtos.MonthlyStats;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final TaskRepository taskRepository;

    @Transactional(readOnly = true)
    public MonthlyStats monthly(TeamMembership viewer, int year, int month) {
        Long teamId = viewer.getTeam().getId();
        ZoneId zone = ZoneId.systemDefault();
        Instant from = YearMonth.of(year, month).atDay(1).atStartOfDay(zone).toInstant();
        Instant to = YearMonth.of(year, month).plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        List<Task> tasks = taskRepository.findAllCreatedBetween(teamId, from, to);

        long created = tasks.size();
        long completed = tasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
        long open = tasks.stream().filter(t -> t.getStatus() == TaskStatus.OPEN).count();
        long inProgress = tasks.stream().filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS).count();
        long testing = tasks.stream().filter(t -> t.getStatus() == TaskStatus.TESTING).count();

        Map<Long, List<Task>> byAssignee = new LinkedHashMap<>();
        for (Task task : tasks) {
            if (task.getAssignee() != null) {
                byAssignee.computeIfAbsent(task.getAssignee().getId(), k -> new ArrayList<>()).add(task);
            }
        }

        List<EmployeeStats> perEmployee = new ArrayList<>();
        for (List<Task> employeeTasks : byAssignee.values()) {
            Task first = employeeTasks.getFirst();
            long taken = employeeTasks.size();
            long done = employeeTasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
            long working = employeeTasks.stream().filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS).count();
            long overdue = employeeTasks.stream()
                    .filter(t -> t.getDeadline() != null)
                    .filter(t -> {
                        LocalDate finished = t.getCompletedAt() != null
                                ? t.getCompletedAt().atZone(zone).toLocalDate()
                                : LocalDate.now(zone);
                        return finished.isAfter(t.getDeadline()) && t.getStatus() != TaskStatus.CANCELLED;
                    })
                    .count();
            OptionalDouble avgHours = employeeTasks.stream()
                    .filter(t -> t.getTakenAt() != null
                            && (t.getSubmittedAt() != null || t.getCompletedAt() != null))
                    .mapToDouble(t -> Duration.between(t.getTakenAt(),
                            t.getSubmittedAt() != null ? t.getSubmittedAt() : t.getCompletedAt())
                            .toMinutes() / 60.0)
                    .average();
            perEmployee.add(new EmployeeStats(
                    first.getAssignee().getId(),
                    first.getAssignee().getFullName(),
                    taken, done, working, overdue,
                    avgHours.isPresent() ? Math.round(avgHours.getAsDouble() * 10) / 10.0 : null));
        }
        perEmployee.sort(Comparator.comparingLong(EmployeeStats::completed).reversed());

        return new MonthlyStats(year, month, created, completed, open, inProgress, testing, perEmployee);
    }
}
