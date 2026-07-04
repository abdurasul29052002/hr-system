package uz.sonic.hr.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.sonic.hr.entity.TicketMessage;

import java.util.List;

public interface TicketMessageRepository extends JpaRepository<TicketMessage, Long> {

    List<TicketMessage> findAllByTicketIdOrderByCreatedAtAsc(Long ticketId);

    long countByTicketId(Long ticketId);
}
