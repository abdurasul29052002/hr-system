package uz.sonic.hr.ticket;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.sonic.hr.ticket.Ticket;
import uz.sonic.hr.common.enums.TicketStatus;

import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // User's own tickets
    List<Ticket> findAllByCreatorIdOrderByCreatedAtDesc(Long creatorId);

    // Admin: all tickets
    List<Ticket> findAllByOrderByCreatedAtDesc();

    // Admin: tickets by status
    List<Ticket> findAllByStatusOrderByCreatedAtDesc(TicketStatus status);

    // Count by status
    long countByStatus(TicketStatus status);
}
