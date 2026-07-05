package uz.sonic.hr.team;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.sonic.hr.team.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {
}
