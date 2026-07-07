package uz.sonic.hr.task;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import uz.sonic.hr.common.enums.TaskStatus;
import uz.sonic.hr.common.security.CurrentMembership;
import uz.sonic.hr.task.TaskService;
import uz.sonic.hr.common.dto.Dtos.AssignRequest;
import uz.sonic.hr.common.dto.Dtos.TaskDto;
import uz.sonic.hr.common.dto.Dtos.TaskRequest;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final CurrentMembership currentMembership;

    @GetMapping
    public List<TaskDto> list(@RequestParam(required = false) TaskStatus status,
                              @RequestParam(required = false, defaultValue = "false") boolean mine) {
        var membership = currentMembership.get();
        Long assigneeId = mine ? membership.getEmployee().getId() : null;
        return taskService.list(status, assigneeId, membership);
    }

    @GetMapping("/{id}")
    public TaskDto get(@PathVariable Long id) {
        return taskService.getDto(id, currentMembership.get());
    }

    @PostMapping
    public TaskDto create(@Valid @RequestBody TaskRequest request) {
        return taskService.create(request, currentMembership.get());
    }

    @PutMapping("/{id}")
    public TaskDto update(@PathVariable Long id, @Valid @RequestBody TaskRequest request) {
        return taskService.update(id, request, currentMembership.get());
    }

    @PostMapping("/{id}/assign")
    public TaskDto assign(@PathVariable Long id, @Valid @RequestBody AssignRequest request) {
        return taskService.assign(id, request.employeeId(), currentMembership.get());
    }

    @PostMapping("/{id}/take")
    public TaskDto take(@PathVariable Long id) {
        return taskService.take(id, currentMembership.get());
    }

    @PostMapping("/{id}/complete")
    public TaskDto complete(@PathVariable Long id) {
        return taskService.complete(id, currentMembership.get());
    }

    @PostMapping("/{id}/approve")
    public TaskDto approve(@PathVariable Long id) {
        return taskService.approve(id, currentMembership.get());
    }

    @PostMapping("/{id}/reject")
    public TaskDto reject(@PathVariable Long id) {
        return taskService.reject(id, currentMembership.get());
    }

    @PostMapping("/{id}/release")
    public TaskDto release(@PathVariable Long id) {
        return taskService.release(id, currentMembership.get());
    }

    @PostMapping("/{id}/cancel")
    public TaskDto cancel(@PathVariable Long id) {
        return taskService.cancel(id, currentMembership.get());
    }

    /** A member self-reports what they are working on → creates a PENDING proposal for a leader to confirm. */
    @PostMapping("/propose")
    public TaskDto propose(@Valid @RequestBody TaskRequest request) {
        return taskService.propose(request, currentMembership.get());
    }

    /** Leader/manager confirms a proposal → it becomes a real (in-progress) task visible to the team. */
    @PostMapping("/{id}/approve-proposal")
    public TaskDto approveProposal(@PathVariable Long id) {
        return taskService.approveProposal(id, currentMembership.get());
    }

    /** Leader/manager declines a proposal → it is deleted. */
    @PostMapping("/{id}/reject-proposal")
    public void rejectProposal(@PathVariable Long id) {
        taskService.rejectProposal(id, currentMembership.get());
    }
}
