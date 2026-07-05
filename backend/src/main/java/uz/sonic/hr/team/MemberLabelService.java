package uz.sonic.hr.team;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uz.sonic.hr.common.dto.Dtos.MemberLabelDto;
import uz.sonic.hr.common.dto.Dtos.MemberLabelRequest;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberLabelService {

    private final MemberLabelRepository labelRepository;

    @Transactional(readOnly = true)
    public List<MemberLabelDto> list(TeamMembership viewer) {
        return labelRepository.findAllByTeamIdOrderByNameAsc(viewer.getTeam().getId())
                .stream().map(MemberLabelDto::from).toList();
    }

    @Transactional
    public MemberLabelDto create(MemberLabelRequest request, TeamMembership actor) {
        TeamService.requireManager(actor);
        if (labelRepository.existsByTeamIdAndNameIgnoreCase(actor.getTeam().getId(), request.name().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Label already exists");
        }
        MemberLabel label = MemberLabel.builder()
                .name(request.name().trim())
                .color(request.color())
                .team(actor.getTeam())
                .build();
        return MemberLabelDto.from(labelRepository.save(label));
    }

    @Transactional
    public MemberLabelDto update(Long id, MemberLabelRequest request, TeamMembership actor) {
        TeamService.requireManager(actor);
        MemberLabel label = getInTeam(id, actor);
        label.setName(request.name().trim());
        label.setColor(request.color());
        return MemberLabelDto.from(labelRepository.save(label));
    }

    @Transactional
    public void delete(Long id, TeamMembership actor) {
        TeamService.requireManager(actor);
        MemberLabel label = getInTeam(id, actor);
        labelRepository.removeLabelFromMembers(label.getId());
        labelRepository.delete(label);
    }

    private MemberLabel getInTeam(Long id, TeamMembership actor) {
        return labelRepository.findById(id)
                .filter(l -> l.getTeam().getId().equals(actor.getTeam().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Label not found"));
    }
}
