package uz.sonic.hr.task;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.sonic.hr.task.TaskAttachment;

import java.util.List;

public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, Long> {

    List<TaskAttachment> findAllByTaskIdOrderByUploadedAtDesc(Long taskId);

    long countByTaskId(Long taskId);
}
