package uz.sonic.hr.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uz.sonic.hr.task.CommentAttachment;

import java.util.List;

public interface CommentAttachmentRepository extends JpaRepository<CommentAttachment, Long> {

    List<CommentAttachment> findAllByCommentId(Long commentId);

    /** Every stored S3 key still in use — the orphan-upload sweep keeps these. */
    @Query("select a.filePath from CommentAttachment a")
    List<String> findAllFilePaths();
}
