package uz.sonic.hr.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.sonic.hr.task.Task;
import uz.sonic.hr.common.enums.TaskStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findAllByTeamIdAndStatusOrderByPriorityDescCreatedAtDesc(Long teamId, TaskStatus status);

    List<Task> findAllByTeamIdAndStatusInOrderByPriorityDescCreatedAtDesc(Long teamId, Collection<TaskStatus> statuses);

    /**
     * Active tasks for the live "who is doing what" view, with assignee AND reviewer fetch-joined so the
     * service can group a task under both without an N+1. Both are @ManyToOne, so no cartesian blow-up.
     */
    @Query("""
            select t from Task t
            left join fetch t.assignee
            left join fetch t.reviewer
            where t.team.id = :teamId and t.status in :statuses
            order by t.priority desc, t.createdAt desc
            """)
    List<Task> findActiveWithParticipants(@Param("teamId") Long teamId,
                                          @Param("statuses") Collection<TaskStatus> statuses);

    List<Task> findAllByTeamIdAndAssigneeIdOrderByCreatedAtDesc(Long teamId, Long assigneeId);

    List<Task> findAllByTeamIdAndAssigneeIdAndStatusOrderByCreatedAtDesc(Long teamId, Long assigneeId,
                                                                         TaskStatus status);

    List<Task> findAllByTeamIdOrderByCreatedAtDesc(Long teamId);

    @Query("""
            select t from Task t
            where t.team.id = :teamId and t.createdAt >= :from and t.createdAt < :to
            """)
    List<Task> findAllCreatedBetween(@Param("teamId") Long teamId,
                                     @Param("from") Instant from,
                                     @Param("to") Instant to);

    /** DONE tasks whose completion falls in the window — powers "employee of the month" and reviewer credit
     *  by work actually finished that month (independent of when the task was created). Assignee AND reviewer
     *  fetch-joined to avoid N+1 (both @ManyToOne, so no cartesian blow-up). */
    @Query("""
            select t from Task t
            left join fetch t.assignee
            left join fetch t.reviewer
            where t.team.id = :teamId
              and t.status = uz.sonic.hr.common.enums.TaskStatus.DONE
              and t.completedAt >= :from and t.completedAt < :to
            """)
    List<Task> findAllCompletedBetween(@Param("teamId") Long teamId,
                                       @Param("from") Instant from,
                                       @Param("to") Instant to);

    /**
     * Tasks whose lifespan overlaps the window: created before it ends and not finished before it starts.
     * CANCELLED tasks are excluded — they never get a completedAt, so a null-means-ongoing predicate would
     * otherwise leak them onto every later month forever. Assignee is fetch-joined to avoid an N+1 during
     * DTO mapping.
     */
    @Query("""
            select t from Task t
            left join fetch t.assignee
            where t.team.id = :teamId
              and t.status <> uz.sonic.hr.common.enums.TaskStatus.CANCELLED
              and t.createdAt < :to
              and (t.completedAt is null or t.completedAt >= :from)
            order by coalesce(t.takenAt, t.createdAt) asc
            """)
    List<Task> findAllOverlapping(@Param("teamId") Long teamId,
                                  @Param("from") Instant from,
                                  @Param("to") Instant to);
}
