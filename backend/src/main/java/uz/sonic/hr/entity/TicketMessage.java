package uz.sonic.hr.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ticket_messages", indexes = {
        @Index(name = "idx_ticket_messages_ticket", columnList = "ticket_id,created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private Employee sender;

    @Column(nullable = false, length = 4000)
    private String message;

    @Builder.Default
    @Column(nullable = false)
    private boolean isAdminResponse = false;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
