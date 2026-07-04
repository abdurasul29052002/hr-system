package uz.sonic.hr.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.sonic.hr.entity.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {
}
