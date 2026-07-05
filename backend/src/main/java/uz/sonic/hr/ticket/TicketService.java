package uz.sonic.hr.ticket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uz.sonic.hr.common.enums.*;
import uz.sonic.hr.common.entity.*;
import uz.sonic.hr.employee.*;
import uz.sonic.hr.admin.*;
import uz.sonic.hr.team.*;
import uz.sonic.hr.task.*;
import uz.sonic.hr.notification.*;
import uz.sonic.hr.ticket.*;
import uz.sonic.hr.ticket.TicketMessageRepository;
import uz.sonic.hr.ticket.TicketRepository;
import uz.sonic.hr.common.dto.Dtos.*;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketMessageRepository messageRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public TicketDto createTicket(CreateTicketRequest request, Employee creator) {
        Ticket ticket = Ticket.builder()
                .creator(creator)
                .subject(request.subject())
                .description(request.description())
                .type(request.type())
                .priority(request.priority() != null ? request.priority() : TicketPriority.MEDIUM)
                .build();

        ticket = ticketRepository.save(ticket);

        // Publish event for admin notification
        eventPublisher.publishEvent(new TicketEvents.TicketCreated(
                ticket.getId(), ticket.getSubject(), creator.getFullName(), creator.getId()));

        return TicketDto.from(ticket);
    }

    @Transactional(readOnly = true)
    public List<TicketDto> getMyTickets(Long employeeId) {
        List<Ticket> tickets = ticketRepository.findAllByCreatorIdOrderByCreatedAtDesc(employeeId);
        return tickets.stream().map(TicketDto::from).toList();
    }

    /** Employee viewing one of their own tickets. */
    @Transactional(readOnly = true)
    public TicketDetailDto getTicket(Long ticketId, Employee viewer) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        if (!ticket.getCreator().getId().equals(viewer.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return TicketDetailDto.from(ticket);
    }

    /** Admin viewing any ticket. */
    @Transactional(readOnly = true)
    public TicketDetailDto getTicketAsAdmin(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        return TicketDetailDto.from(ticket);
    }

    @Transactional
    public TicketMessageDto addMessage(Long ticketId, String messageText, Employee sender) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));

        // Security: only creator can add messages (admin uses respondToTicket)
        if (!ticket.getCreator().getId().equals(sender.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ticket creator can add messages");
        }

        TicketMessage message = TicketMessage.builder()
                .ticket(ticket)
                .sender(sender)
                .message(messageText)
                .isAdminResponse(false)
                .build();

        message = messageRepository.save(message);

        ticket.setUpdatedAt(Instant.now());
        ticketRepository.save(ticket);

        // Notify admins
        eventPublisher.publishEvent(new TicketEvents.TicketUserMessage(
                ticket.getId(), ticket.getSubject(), sender.getFullName()));

        return TicketMessageDto.from(message);
    }

    @Transactional(readOnly = true)
    public List<TicketDto> getAllTickets(TicketStatus status) {
        List<Ticket> tickets;
        if (status != null) {
            tickets = ticketRepository.findAllByStatusOrderByCreatedAtDesc(status);
        } else {
            tickets = ticketRepository.findAllByOrderByCreatedAtDesc();
        }
        return tickets.stream().map(TicketDto::from).toList();
    }

    @Transactional
    public TicketMessageDto respondToTicket(Long ticketId, String messageText, Admin admin) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));

        TicketMessage message = TicketMessage.builder()
                .ticket(ticket)
                .adminSender(admin)
                .message(messageText)
                .isAdminResponse(true)
                .build();

        message = messageRepository.save(message);

        ticket.setUpdatedAt(Instant.now());
        ticketRepository.save(ticket);

        // Notify ticket creator
        eventPublisher.publishEvent(new TicketEvents.TicketAdminResponse(
                ticket.getId(), ticket.getSubject(), ticket.getCreator().getId(), admin.getFullName()));

        return TicketMessageDto.from(message);
    }

    @Transactional
    public TicketDto updateStatus(Long ticketId, TicketStatus newStatus, Admin admin) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));

        TicketStatus oldStatus = ticket.getStatus();
        ticket.setStatus(newStatus);
        ticket.setUpdatedAt(Instant.now());

        if (newStatus == TicketStatus.RESOLVED || newStatus == TicketStatus.CLOSED) {
            ticket.setResolvedAt(Instant.now());
            ticket.setResolvedBy(admin);
        }

        ticket = ticketRepository.save(ticket);

        // Notify ticket creator if status changed
        if (oldStatus != newStatus) {
            eventPublisher.publishEvent(new TicketEvents.TicketStatusChanged(
                    ticket.getId(), ticket.getSubject(), ticket.getCreator().getId(), newStatus, admin.getFullName()));
        }

        return TicketDto.from(ticket);
    }

    @Transactional
    public TicketDto updatePriority(Long ticketId, TicketPriority priority, Admin admin) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));

        ticket.setPriority(priority);
        ticket.setUpdatedAt(Instant.now());
        ticket = ticketRepository.save(ticket);

        return TicketDto.from(ticket);
    }

    @Transactional(readOnly = true)
    public TicketStatsDto getStats() {
        long open = ticketRepository.countByStatus(TicketStatus.OPEN);
        long inProgress = ticketRepository.countByStatus(TicketStatus.IN_PROGRESS);
        long resolved = ticketRepository.countByStatus(TicketStatus.RESOLVED);
        long total = ticketRepository.count();

        return new TicketStatsDto(open, inProgress, resolved, total);
    }
}
