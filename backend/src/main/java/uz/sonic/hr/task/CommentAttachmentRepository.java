package uz.sonic.hr.task;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.sonic.hr.task.CommentAttachment;

import java.util.List;

public interface CommentAttachmentRepository extends JpaRepository<CommentAttachment, Long> {

    List<CommentAttachment> findAllByCommentId(Long commentId);
}
