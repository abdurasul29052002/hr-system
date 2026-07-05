package uz.sonic.hr.team;

import uz.sonic.hr.common.enums.*;
import uz.sonic.hr.employee.*;
import uz.sonic.hr.admin.*;
import uz.sonic.hr.team.*;
import uz.sonic.hr.task.*;
import uz.sonic.hr.ticket.*;
import uz.sonic.hr.notification.*;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "team_memberships", uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "team_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id")
    private Team team;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.MEMBER;

    /** Per-team job title. */
    private String position;

    /** Team-scoped specialization labels (e.g. Backend/Frontend developer). */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "membership_labels",
            joinColumns = @JoinColumn(name = "membership_id"),
            inverseJoinColumns = @JoinColumn(name = "label_id"))
    @BatchSize(size = 50)
    @Builder.Default
    private Set<MemberLabel> labels = new LinkedHashSet<>();

    @Builder.Default
    private Instant joinedAt = Instant.now();
}
