package uz.sonic.hr.ticket;

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

    /** Set for messages written by the ticket's employee creator; null for admin replies. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private Employee sender;

    /** Set for admin replies; null for employee messages. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_sender_id")
    private Admin adminSender;

    @Column(nullable = false, length = 4000)
    private String message;

    @Builder.Default
    @Column(nullable = false)
    private boolean isAdminResponse = false;

    /** Display name of whoever sent the message (employee or admin). */
    @Transient
    public String senderName() {
        if (adminSender != null) {
            return adminSender.getFullName();
        }
        return sender != null ? sender.getFullName() : "—";
    }

    /** Id of whoever sent the message (employee or admin). */
    @Transient
    public Long senderId() {
        if (adminSender != null) {
            return adminSender.getId();
        }
        return sender != null ? sender.getId() : null;
    }

    @Builder.Default
    private Instant createdAt = Instant.now();
}
