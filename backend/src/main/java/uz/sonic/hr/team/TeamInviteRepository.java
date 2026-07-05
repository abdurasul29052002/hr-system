package uz.sonic.hr.team;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.sonic.hr.team.TeamInvite;

import java.util.List;
import java.util.Optional;

public interface TeamInviteRepository extends JpaRepository<TeamInvite, Long> {

    Optional<TeamInvite> findByToken(String token);

    List<TeamInvite> findAllByTeamIdAndRevokedFalseOrderByCreatedAtDesc(Long teamId);
}
