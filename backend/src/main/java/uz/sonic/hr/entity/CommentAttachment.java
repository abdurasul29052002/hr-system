package uz.sonic.hr.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "comment_attachments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    private TaskComment comment;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false, length = 500)
    private String filePath; // S3 key

    @Column(nullable = false)
    private Long fileSize;

    private String mimeType;

    @Builder.Default
    private Instant uploadedAt = Instant.now();
}
