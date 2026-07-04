package uz.sonic.hr.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.sonic.hr.entity.TeamInvite;

import java.util.List;
import java.util.Optional;

public interface TeamInviteRepository extends JpaRepository<TeamInvite, Long> {

    Optional<TeamInvite> findByToken(String token);

    List<TeamInvite> findAllByTeamIdAndRevokedFalseOrderByCreatedAtDesc(Long teamId);
}
