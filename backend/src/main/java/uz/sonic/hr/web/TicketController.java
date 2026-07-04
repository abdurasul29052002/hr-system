package uz.sonic.hr.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.sonic.hr.entity.Employee;
import uz.sonic.hr.repo.EmployeeRepository;
import uz.sonic.hr.security.UserPrincipal;
import uz.sonic.hr.service.TicketService;
import uz.sonic.hr.web.dto.Dtos.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final EmployeeRepository employeeRepository;

    @PostMapping
    public TicketDto createTicket(@Valid @RequestBody CreateTicketRequest request,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        Employee employee = employeeRepository.findById(principal.getEmployeeId()).orElseThrow();
        return ticketService.createTicket(request, employee);
    }

    @GetMapping("/my")
    public List<TicketDto> getMyTickets(@AuthenticationPrincipal UserPrincipal principal) {
        return ticketService.getMyTickets(principal.getEmployeeId());
    }

    @GetMapping("/{id}")
    public TicketDetailDto getTicket(@PathVariable Long id,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        Employee employee = employeeRepository.findById(principal.getEmployeeId()).orElseThrow();
        return ticketService.getTicket(id, employee);
    }

    @PostMapping("/{id}/messages")
    public TicketMessageDto addMessage(@PathVariable Long id,
                                        @Valid @RequestBody AddMessageRequest request,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        Employee employee = employeeRepository.findById(principal.getEmployeeId()).orElseThrow();
        return ticketService.addMessage(id, request.message(), employee);
    }
}
