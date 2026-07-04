package uz.sonic.hr.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.sonic.hr.entity.Employee;
import uz.sonic.hr.entity.TicketStatus;
import uz.sonic.hr.repo.EmployeeRepository;
import uz.sonic.hr.security.UserPrincipal;
import uz.sonic.hr.service.TicketService;
import uz.sonic.hr.web.dto.Dtos.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/tickets")
@RequiredArgsConstructor
public class AdminTicketController {

    private final TicketService ticketService;
    private final EmployeeRepository employeeRepository;

    @GetMapping
    public List<TicketDto> getAllTickets(@RequestParam(required = false) TicketStatus status,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        Employee admin = employeeRepository.findById(principal.getEmployeeId()).orElseThrow();
        // Service will check admin permission
        return ticketService.getAllTickets(status);
    }

    @GetMapping("/{id}")
    public TicketDetailDto getTicket(@PathVariable Long id,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        Employee admin = employeeRepository.findById(principal.getEmployeeId()).orElseThrow();
        return ticketService.getTicket(id, admin);
    }

    @PostMapping("/{id}/respond")
    public TicketMessageDto respondToTicket(@PathVariable Long id,
                                             @Valid @RequestBody AddMessageRequest request,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        Employee admin = employeeRepository.findById(principal.getEmployeeId()).orElseThrow();
        return ticketService.respondToTicket(id, request.message(), admin);
    }

    @PatchMapping("/{id}/status")
    public TicketDto updateStatus(@PathVariable Long id,
                                   @Valid @RequestBody UpdateTicketStatusRequest request,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        Employee admin = employeeRepository.findById(principal.getEmployeeId()).orElseThrow();
        return ticketService.updateStatus(id, request.status(), admin);
    }

    @PatchMapping("/{id}/priority")
    public TicketDto updatePriority(@PathVariable Long id,
                                     @Valid @RequestBody UpdateTicketPriorityRequest request,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        Employee admin = employeeRepository.findById(principal.getEmployeeId()).orElseThrow();
        return ticketService.updatePriority(id, request.priority(), admin);
    }

    @GetMapping("/stats")
    public TicketStatsDto getStats(@AuthenticationPrincipal UserPrincipal principal) {
        Employee admin = employeeRepository.findById(principal.getEmployeeId()).orElseThrow();
        if (!admin.isAdmin()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Admin only");
        }
        return ticketService.getStats();
    }
}
