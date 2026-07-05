package uz.sonic.hr.task;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.sonic.hr.task.TaskComment;

import java.util.List;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {

    List<TaskComment> findAllByTaskIdOrderByCreatedAtAsc(Long taskId);

    long countByTaskId(Long taskId);
}
