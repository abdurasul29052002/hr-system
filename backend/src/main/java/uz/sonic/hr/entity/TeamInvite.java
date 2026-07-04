package uz.sonic.hr.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "team_invites")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(optional = false)
    private Team team;

    /** Role granted to whoever joins with this link. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.MEMBER;

    @ManyToOne(optional = false)
    private Employee createdBy;

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant expiresAt;

    @Builder.Default
    private boolean revoked = false;

    public boolean isUsable() {
        return !revoked && (expiresAt == null || expiresAt.isAfter(Instant.now()));
    }
}
