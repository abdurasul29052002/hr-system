package uz.sonic.hr.task;

import uz.sonic.hr.common.enums.*;
import uz.sonic.hr.employee.*;
import uz.sonic.hr.admin.*;
import uz.sonic.hr.team.*;
import uz.sonic.hr.task.*;
import uz.sonic.hr.ticket.*;
import uz.sonic.hr.notification.*;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TaskStatus status = TaskStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Team team;

    @ManyToMany
    @JoinTable(name = "task_tags",
            joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    @Builder.Default
    private Set<Tag> tags = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Employee createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    private Employee assignee;

    /**
     * Designated reviewer/tester (GitHub-PR style). Set when creating/assigning/editing the task; the
     * reviewer OR any manager can approve/reject the submitted work. On approve/reject it is updated to
     * whoever actually reviewed, so the task always shows "who did it" (assignee) + "who reviewed it".
     */
    @ManyToOne(fetch = FetchType.LAZY)
    private Employee reviewer;

    /** Work deadline: the assignee must SUBMIT the task for review by this date. */
    private LocalDate deadline;

    /** Test/review deadline: the reviewer must approve (finish reviewing) by this date. */
    private LocalDate reviewDeadline;

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant takenAt;

    /** When the assignee submitted the task for review (moved to TESTING). */
    private Instant submittedAt;

    private Instant completedAt;
}
