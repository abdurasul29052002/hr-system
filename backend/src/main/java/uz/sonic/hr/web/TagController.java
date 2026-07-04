package uz.sonic.hr.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import uz.sonic.hr.security.CurrentMembership;
import uz.sonic.hr.service.TagService;
import uz.sonic.hr.web.dto.Dtos.TagDto;
import uz.sonic.hr.web.dto.Dtos.TagRequest;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;
    private final CurrentMembership currentMembership;

    @GetMapping
    public List<TagDto> list() {
        return tagService.list(currentMembership.get());
    }

    @PostMapping
    public TagDto create(@Valid @RequestBody TagRequest request) {
        return tagService.create(request, currentMembership.get());
    }

    @PutMapping("/{id}")
    public TagDto update(@PathVariable Long id, @Valid @RequestBody TagRequest request) {
        return tagService.update(id, request, currentMembership.get());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        tagService.delete(id, currentMembership.get());
    }
}
