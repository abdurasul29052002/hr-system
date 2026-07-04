package uz.sonic.hr.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uz.sonic.hr.entity.Tag;
import uz.sonic.hr.entity.TeamMembership;
import uz.sonic.hr.repo.TagRepository;
import uz.sonic.hr.web.dto.Dtos.TagDto;
import uz.sonic.hr.web.dto.Dtos.TagRequest;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    @Transactional(readOnly = true)
    public List<TagDto> list(TeamMembership viewer) {
        return tagRepository.findAllByTeamIdOrderByNameAsc(viewer.getTeam().getId())
                .stream().map(TagDto::from).toList();
    }

    @Transactional
    public TagDto create(TagRequest request, TeamMembership actor) {
        TeamService.requireManager(actor);
        if (tagRepository.existsByTeamIdAndNameIgnoreCase(actor.getTeam().getId(), request.name().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tag already exists");
        }
        Tag tag = Tag.builder()
                .name(request.name().trim())
                .color(request.color())
                .team(actor.getTeam())
                .build();
        return TagDto.from(tagRepository.save(tag));
    }

    @Transactional
    public TagDto update(Long id, TagRequest request, TeamMembership actor) {
        TeamService.requireManager(actor);
        Tag tag = getInTeam(id, actor);
        tag.setName(request.name().trim());
        tag.setColor(request.color());
        return TagDto.from(tagRepository.save(tag));
    }

    @Transactional
    public void delete(Long id, TeamMembership actor) {
        TeamService.requireManager(actor);
        Tag tag = getInTeam(id, actor);
        tagRepository.removeTagFromTasks(tag.getId());
        tagRepository.delete(tag);
    }

    private Tag getInTeam(Long id, TeamMembership actor) {
        return tagRepository.findById(id)
                .filter(tag -> tag.getTeam().getId().equals(actor.getTeam().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tag not found"));
    }
}
