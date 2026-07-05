package uz.sonic.hr.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import uz.sonic.hr.common.enums.TicketStatus;
import uz.sonic.hr.common.security.CurrentAdmin;
import uz.sonic.hr.ticket.TicketService;
import uz.sonic.hr.common.dto.Dtos.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/tickets")
@RequiredArgsConstructor
public class AdminTicketController {

    private final TicketService ticketService;
    private final CurrentAdmin currentAdmin;

    @GetMapping
    public List<TicketDto> getAllTickets(@RequestParam(required = false) TicketStatus status) {
        currentAdmin.get();
        return ticketService.getAllTickets(status);
    }

    @GetMapping("/{id}")
    public TicketDetailDto getTicket(@PathVariable Long id) {
        currentAdmin.get();
        return ticketService.getTicketAsAdmin(id);
    }

    @PostMapping("/{id}/respond")
    public TicketMessageDto respondToTicket(@PathVariable Long id,
                                             @Valid @RequestBody AddMessageRequest request) {
        return ticketService.respondToTicket(id, request.message(), currentAdmin.get());
    }

    @PatchMapping("/{id}/status")
    public TicketDto updateStatus(@PathVariable Long id,
                                   @Valid @RequestBody UpdateTicketStatusRequest request) {
        return ticketService.updateStatus(id, request.status(), currentAdmin.get());
    }

    @PatchMapping("/{id}/priority")
    public TicketDto updatePriority(@PathVariable Long id,
                                     @Valid @RequestBody UpdateTicketPriorityRequest request) {
        return ticketService.updatePriority(id, request.priority(), currentAdmin.get());
    }

    @GetMapping("/stats")
    public TicketStatsDto getStats() {
        currentAdmin.get();
        return ticketService.getStats();
    }
}
