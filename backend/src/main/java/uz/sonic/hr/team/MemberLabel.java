package uz.sonic.hr.team;

import jakarta.persistence.*;
import lombok.*;

/**
 * A team-scoped specialization label for members (e.g. "Backend developer",
 * "Frontend developer"). Independent of the per-team {@code role}; a MEMBER can
 * carry any number of these labels.
 */
@Entity
@Table(name = "member_labels", uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberLabel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** Hex color like #4f46e5 */
    private String color;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id")
    private Team team;
}
