package uz.sonic.hr.team;

import jakarta.persistence.*;
import lombok.*;
import uz.sonic.hr.common.enums.JoinRequestStatus;
import uz.sonic.hr.employee.Employee;

import java.time.Instant;

@Entity
@Table(name = "team_join_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamJoinRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private JoinRequestStatus status = JoinRequestStatus.PENDING;

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant decidedAt;
}
