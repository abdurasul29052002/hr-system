package uz.sonic.hr.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "comment_mentions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"comment_id", "mentioned_employee_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentMention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    private TaskComment comment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentioned_employee_id", nullable = false)
    private Employee mentionedEmployee;
}
