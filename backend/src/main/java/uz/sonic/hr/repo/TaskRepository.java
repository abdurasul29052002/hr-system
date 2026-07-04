package uz.sonic.hr.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.sonic.hr.entity.Task;
import uz.sonic.hr.entity.TaskStatus;

import java.time.Instant;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findAllByTeamIdAndStatusOrderByPriorityDescCreatedAtDesc(Long teamId, TaskStatus status);

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
}
