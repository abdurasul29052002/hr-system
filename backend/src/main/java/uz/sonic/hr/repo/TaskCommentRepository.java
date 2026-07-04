package uz.sonic.hr.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.sonic.hr.entity.TaskComment;

import java.util.List;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {

    List<TaskComment> findAllByTaskIdOrderByCreatedAtAsc(Long taskId);

    long countByTaskId(Long taskId);
}
