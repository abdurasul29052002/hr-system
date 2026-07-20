package uz.sonic.hr.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uz.sonic.hr.task.TaskAttachment;

import java.util.List;

public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, Long> {

    List<TaskAttachment> findAllByTaskIdOrderByUploadedAtDesc(Long taskId);

    long countByTaskId(Long taskId);

    /** Every stored S3 key still in use — the orphan-upload sweep keeps these. */
    @Query("select a.filePath from TaskAttachment a")
    List<String> findAllFilePaths();
}
