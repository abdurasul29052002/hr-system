package uz.sonic.hr.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.sonic.hr.entity.TaskAttachment;

import java.util.List;

public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, Long> {

    List<TaskAttachment> findAllByTaskIdOrderByUploadedAtDesc(Long taskId);

    long countByTaskId(Long taskId);
}
