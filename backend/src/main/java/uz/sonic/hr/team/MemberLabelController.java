package uz.sonic.hr.team;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import uz.sonic.hr.common.security.CurrentMembership;
import uz.sonic.hr.common.dto.Dtos.MemberLabelDto;
import uz.sonic.hr.common.dto.Dtos.MemberLabelRequest;

import java.util.List;

@RestController
@RequestMapping("/api/member-labels")
@RequiredArgsConstructor
public class MemberLabelController {

    private final MemberLabelService labelService;
    private final CurrentMembership currentMembership;

    @GetMapping
    public List<MemberLabelDto> list() {
        return labelService.list(currentMembership.get());
    }

    @PostMapping
    public MemberLabelDto create(@Valid @RequestBody MemberLabelRequest request) {
        return labelService.create(request, currentMembership.get());
    }

    @PutMapping("/{id}")
    public MemberLabelDto update(@PathVariable Long id, @Valid @RequestBody MemberLabelRequest request) {
        return labelService.update(id, request, currentMembership.get());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        labelService.delete(id, currentMembership.get());
    }
}
