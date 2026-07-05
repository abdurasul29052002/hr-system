package uz.sonic.hr.ticket;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import uz.sonic.hr.employee.Employee;
import uz.sonic.hr.common.security.CurrentUser;
import uz.sonic.hr.ticket.TicketService;
import uz.sonic.hr.common.dto.Dtos.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final CurrentUser currentUser;

    @PostMapping
    public TicketDto createTicket(@Valid @RequestBody CreateTicketRequest request) {
        return ticketService.createTicket(request, currentUser.get());
    }

    @GetMapping("/my")
    public List<TicketDto> getMyTickets() {
        return ticketService.getMyTickets(currentUser.get().getId());
    }

    @GetMapping("/{id}")
    public TicketDetailDto getTicket(@PathVariable Long id) {
        return ticketService.getTicket(id, currentUser.get());
    }

    @PostMapping("/{id}/messages")
    public TicketMessageDto addMessage(@PathVariable Long id,
                                        @Valid @RequestBody AddMessageRequest request) {
        Employee employee = currentUser.get();
        return ticketService.addMessage(id, request.message(), employee);
    }
}
