package uz.sonic.hr.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.sonic.hr.entity.Ticket;
import uz.sonic.hr.entity.TicketStatus;

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
