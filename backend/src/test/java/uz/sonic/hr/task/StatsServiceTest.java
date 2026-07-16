package uz.sonic.hr.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.sonic.hr.common.dto.Dtos.MemberActivityDto;
import uz.sonic.hr.common.dto.Dtos.MonthlyStats;
import uz.sonic.hr.common.dto.Dtos.StatusSlice;
import uz.sonic.hr.common.dto.Dtos.TimelineTaskDto;
import uz.sonic.hr.common.enums.TaskStatus;
import uz.sonic.hr.employee.Employee;
import uz.sonic.hr.team.Team;
import uz.sonic.hr.team.TeamMembership;
import uz.sonic.hr.team.TeamMembershipRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for the stats aggregation logic — repositories are mocked, so it is fast and DB-free.
 * All "overdue" assertions use DONE tasks (deadline vs completedAt) so the result is deterministic,
 * independent of today's date.
 */
class StatsServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Tashkent");
    private static final long TEAM_ID = 1L;

    private final TaskRepository taskRepo = mock(TaskRepository.class);
    private final TeamMembershipRepository memberRepo = mock(TeamMembershipRepository.class);
    private final StatsService stats = new StatsService(taskRepo, memberRepo);

    private Employee jasur;
    private Employee nodira;
    private TeamMembership viewer;

    private Instant on(int day, int hour) {
        return LocalDate.of(2026, 7, day).atTime(hour, 0).atZone(ZONE).toInstant();
    }

    private Employee employee(long id, String name) {
        Employee e = mock(Employee.class);
        when(e.getId()).thenReturn(id);
        when(e.getFullName()).thenReturn(name);
        return e;
    }

    @BeforeEach
    void setUp() {
        jasur = employee(35L, "Jasur");
        nodira = employee(65L, "Nodira");

        Team team = mock(Team.class);
        when(team.getId()).thenReturn(TEAM_ID);
        viewer = mock(TeamMembership.class);
        when(viewer.getTeam()).thenReturn(team);
    }

    private List<Task> julyTasks() {
        // DONE on time (completed 07-05, deadline 07-10)
        Task t1 = Task.builder().id(1L).title("On-time done").status(TaskStatus.DONE).assignee(jasur)
                .deadline(LocalDate.of(2026, 7, 10)).takenAt(on(1, 9)).submittedAt(on(5, 12)).completedAt(on(5, 12))
                .build();
        // DONE late (completed 07-05, deadline 07-02) → overdue
        Task t2 = Task.builder().id(2L).title("Late done").status(TaskStatus.DONE).assignee(jasur)
                .deadline(LocalDate.of(2026, 7, 2)).takenAt(on(1, 9)).submittedAt(on(5, 12)).completedAt(on(5, 12))
                .build();
        // IN_PROGRESS, no deadline → not overdue
        Task t3 = Task.builder().id(3L).title("Working").status(TaskStatus.IN_PROGRESS).assignee(nodira)
                .takenAt(on(3, 9)).build();
        // CANCELLED, unassigned → not overdue, not in per-employee
        Task t4 = Task.builder().id(4L).title("Scrapped").status(TaskStatus.CANCELLED).build();
        return List.of(t1, t2, t3, t4);
    }

    @Test
    void monthly_aggregatesTotalsAndOverdueAndEmployeeOfMonth() {
        when(taskRepo.findAllCreatedBetween(eq(TEAM_ID), any(), any())).thenReturn(julyTasks());
        // Employee of the month is now driven by tasks COMPLETED this month (both of Jasur's are DONE).
        when(taskRepo.findAllCompletedBetween(eq(TEAM_ID), any(), any()))
                .thenReturn(julyTasks().stream().filter(t -> t.getStatus() == TaskStatus.DONE).toList());

        MonthlyStats m = stats.monthly(viewer, 2026, 7);

        assertThat(m.totalCreated()).isEqualTo(4);
        assertThat(m.totalCompleted()).isEqualTo(2);
        assertThat(m.totalInProgress()).isEqualTo(1);
        assertThat(m.totalCancelled()).isEqualTo(1);
        assertThat(m.totalOverdue()).isEqualTo(1); // only the late DONE task
        assertThat(m.employeeOfMonth()).isEqualTo("Jasur"); // Jasur completed 2 this month, Nodira 0
    }

    @Test
    void monthly_perEmployeeMetricsAreCorrect() {
        when(taskRepo.findAllCreatedBetween(eq(TEAM_ID), any(), any())).thenReturn(julyTasks());

        MonthlyStats m = stats.monthly(viewer, 2026, 7);

        assertThat(m.perEmployee()).hasSize(2);
        var jasurStats = m.perEmployee().stream().filter(e -> e.employeeId() == 35L).findFirst().orElseThrow();
        assertThat(jasurStats.taken()).isEqualTo(2);
        assertThat(jasurStats.completed()).isEqualTo(2);
        assertThat(jasurStats.onTime()).isEqualTo(1);   // t1 on time, t2 late
        assertThat(jasurStats.overdue()).isEqualTo(1);  // t2
        // sorted by completed desc → Jasur (2) before Nodira (0)
        assertThat(m.perEmployee().get(0).fullName()).isEqualTo("Jasur");
    }

    @Test
    void monthly_distributionPartitionsAllTasks() {
        when(taskRepo.findAllCreatedBetween(eq(TEAM_ID), any(), any())).thenReturn(julyTasks());

        MonthlyStats m = stats.monthly(viewer, 2026, 7);

        Map<String, Long> byCat = m.distribution().stream()
                .collect(Collectors.toMap(StatusSlice::category, StatusSlice::count));
        assertThat(byCat).containsEntry("DONE", 1L)        // t1 (on-time)
                .containsEntry("OVERDUE", 1L)              // t2 (late)
                .containsEntry("IN_PROGRESS", 1L)          // t3
                .containsEntry("CANCELLED", 1L);           // t4
        assertThat(m.distribution().stream().mapToLong(StatusSlice::count).sum())
                .isEqualTo(m.totalCreated());
    }

    @Test
    void timeline_mapsOverlappingTasks() {
        when(taskRepo.findAllOverlapping(eq(TEAM_ID), any(), any())).thenReturn(julyTasks());

        List<TimelineTaskDto> timeline = stats.timeline(viewer, 2026, 7);

        assertThat(timeline).extracting(TimelineTaskDto::title)
                .containsExactly("On-time done", "Late done", "Working", "Scrapped");
        assertThat(timeline.get(0).assigneeName()).isEqualTo("Jasur");
    }

    @Test
    void current_groupsActiveTasksByMember() {
        Task working = julyTasks().get(2); // t3 IN_PROGRESS, nodira
        when(taskRepo.findActiveWithParticipants(eq(TEAM_ID), any()))
                .thenReturn(List.of(working));

        TeamMembership nodiraMember = mock(TeamMembership.class);
        when(nodiraMember.getEmployee()).thenReturn(nodira);
        when(nodiraMember.getLabels()).thenReturn(Set.of());
        when(nodiraMember.getPosition()).thenReturn("Manager");
        when(memberRepo.findAllByTeamIdWithEmployee(TEAM_ID)).thenReturn(List.of(nodiraMember));

        List<MemberActivityDto> activity = stats.current(viewer);

        Map<String, MemberActivityDto> byName = activity.stream()
                .collect(Collectors.toMap(MemberActivityDto::fullName, Function.identity()));
        assertThat(byName).containsKey("Nodira");
        assertThat(byName.get("Nodira").activeTasks()).hasSize(1);
        assertThat(byName.get("Nodira").activeTasks().get(0).title()).isEqualTo("Working");
        assertThat(byName.get("Nodira").activeTasks().get(0).reviewing()).isFalse();
    }

    @Test
    void current_surfacesTestingTaskUnderAssigneeAndReviewer() {
        // Jasur owns it (in TESTING), Nodira reviews it — it must occupy BOTH, differently labelled.
        Task inReview = Task.builder().id(9L).title("Awaiting review").status(TaskStatus.TESTING)
                .assignee(jasur).reviewer(nodira).takenAt(on(2, 9)).submittedAt(on(4, 12)).build();
        when(taskRepo.findActiveWithParticipants(eq(TEAM_ID), any())).thenReturn(List.of(inReview));

        TeamMembership jasurMember = mock(TeamMembership.class);
        when(jasurMember.getEmployee()).thenReturn(jasur);
        when(jasurMember.getLabels()).thenReturn(Set.of());
        TeamMembership nodiraMember = mock(TeamMembership.class);
        when(nodiraMember.getEmployee()).thenReturn(nodira);
        when(nodiraMember.getLabels()).thenReturn(Set.of());
        when(memberRepo.findAllByTeamIdWithEmployee(TEAM_ID)).thenReturn(List.of(jasurMember, nodiraMember));

        Map<String, MemberActivityDto> byName = stats.current(viewer).stream()
                .collect(Collectors.toMap(MemberActivityDto::fullName, Function.identity()));

        // Assignee: has it, not as a review.
        assertThat(byName.get("Jasur").activeTasks()).hasSize(1);
        assertThat(byName.get("Jasur").activeTasks().get(0).reviewing()).isFalse();
        // Reviewer: same task surfaces as "reviewing".
        assertThat(byName.get("Nodira").activeTasks()).hasSize(1);
        assertThat(byName.get("Nodira").activeTasks().get(0).id()).isEqualTo(9L);
        assertThat(byName.get("Nodira").activeTasks().get(0).reviewing()).isTrue();
    }

    @Test
    void monthly_creditsPureReviewersWithNoOwnTasks() {
        // Nodira took no task this month but reviewed Jasur's two DONE tasks → she must appear with reviewed=2.
        Task t1 = Task.builder().id(1L).title("A").status(TaskStatus.DONE).assignee(jasur).reviewer(nodira)
                .deadline(LocalDate.of(2026, 7, 10)).takenAt(on(1, 9)).submittedAt(on(5, 12)).completedAt(on(6, 12))
                .build();
        Task t2 = Task.builder().id(2L).title("B").status(TaskStatus.DONE).assignee(jasur).reviewer(nodira)
                .deadline(LocalDate.of(2026, 7, 10)).takenAt(on(1, 9)).submittedAt(on(5, 12)).completedAt(on(6, 12))
                .build();
        when(taskRepo.findAllCreatedBetween(eq(TEAM_ID), any(), any())).thenReturn(List.of(t1, t2));
        // Reviews are credited by completion month, so the reviewed count comes from findAllCompletedBetween.
        when(taskRepo.findAllCompletedBetween(eq(TEAM_ID), any(), any())).thenReturn(List.of(t1, t2));

        MonthlyStats m = stats.monthly(viewer, 2026, 7);

        var nodiraStats = m.perEmployee().stream().filter(e -> e.employeeId() == 65L).findFirst().orElseThrow();
        assertThat(nodiraStats.taken()).isZero();
        assertThat(nodiraStats.completed()).isZero();
        assertThat(nodiraStats.reviewed()).isEqualTo(2);
        // Jasur (the assignee) is credited as reviewed=0 and appears ahead of the pure reviewer.
        assertThat(m.perEmployee().get(0).fullName()).isEqualTo("Jasur");
    }

    @Test
    void monthly_creditsReviewByCompletionMonthNotCreationMonth() {
        // Task created in a PRIOR month (so findAllCreatedBetween is empty for this month) but reviewed and
        // completed THIS month → the reviewer must still be credited this month, by completion window.
        Task reviewedThisMonth = Task.builder().id(7L).title("Cross-month").status(TaskStatus.DONE)
                .assignee(jasur).reviewer(nodira)
                .takenAt(on(1, 9)).submittedAt(on(2, 9)).completedAt(on(3, 9)).build();
        when(taskRepo.findAllCreatedBetween(eq(TEAM_ID), any(), any())).thenReturn(List.of());
        when(taskRepo.findAllCompletedBetween(eq(TEAM_ID), any(), any())).thenReturn(List.of(reviewedThisMonth));

        MonthlyStats m = stats.monthly(viewer, 2026, 7);

        var nodiraStats = m.perEmployee().stream().filter(e -> e.employeeId() == 65L).findFirst().orElseThrow();
        assertThat(nodiraStats.reviewed()).isEqualTo(1);
    }
}
