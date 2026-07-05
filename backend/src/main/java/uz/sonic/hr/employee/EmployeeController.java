package uz.sonic.hr.employee;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import uz.sonic.hr.common.security.CurrentMembership;
import uz.sonic.hr.employee.EmployeeService;
import uz.sonic.hr.common.dto.Dtos.*;

import java.util.List;

/** Members of the team selected via the X-Team-Id header. */
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;
    private final CurrentMembership currentMembership;

    @GetMapping
    public List<MemberDto> list() {
        return employeeService.listMembers(currentMembership.get()).stream().map(MemberDto::from).toList();
    }

    /** Creates a brand-new user account inside the team. */
    @PostMapping
    public MemberDto create(@Valid @RequestBody NewMemberRequest request) {
        return MemberDto.from(employeeService.createInTeam(request, currentMembership.get()));
    }

    /** Adds an already-registered user to the team by username. */
    @PostMapping("/add-existing")
    public MemberDto addExisting(@Valid @RequestBody AddMemberRequest request) {
        return MemberDto.from(employeeService.addExisting(request, currentMembership.get()));
    }

    /** Updates a member's per-team role/position. */
    @PutMapping("/{employeeId}")
    public MemberDto update(@PathVariable Long employeeId, @Valid @RequestBody UpdateMemberRequest request) {
        return MemberDto.from(employeeService.updateMember(employeeId, request, currentMembership.get()));
    }

    @DeleteMapping("/{employeeId}")
    public void remove(@PathVariable Long employeeId) {
        employeeService.removeFromTeam(employeeId, currentMembership.get());
    }

    @PostMapping("/{employeeId}/reset-telegram")
    public MemberDto resetTelegram(@PathVariable Long employeeId) {
        return MemberDto.from(employeeService.resetTelegram(employeeId, currentMembership.get()));
    }
}
