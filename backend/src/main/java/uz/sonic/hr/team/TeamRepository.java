package uz.sonic.hr.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.sonic.hr.team.Team;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {

    @Query("""
            select t from Team t
            where lower(t.name) like lower(concat('%', :q, '%'))
            order by t.name
            """)
    List<Team> searchByName(@Param("q") String query);
}
