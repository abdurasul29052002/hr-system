package uz.sonic.hr.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.sonic.hr.entity.CommentAttachment;

import java.util.List;

public interface CommentAttachmentRepository extends JpaRepository<CommentAttachment, Long> {

    List<CommentAttachment> findAllByCommentId(Long commentId);
}
