package uz.sonic.hr.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.sonic.hr.entity.Role;
import uz.sonic.hr.entity.TeamMembership;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TeamMembershipRepository extends JpaRepository<TeamMembership, Long> {

    Optional<TeamMembership> findByEmployeeIdAndTeamId(Long employeeId, Long teamId);

    List<TeamMembership> findAllByEmployeeIdOrderByJoinedAtAsc(Long employeeId);

    @Query("""
            select m from TeamMembership m join fetch m.employee e
            where m.team.id = :teamId
            order by e.fullName
            """)
    List<TeamMembership> findAllByTeamIdWithEmployee(@Param("teamId") Long teamId);

    @Query("""
            select m from TeamMembership m join fetch m.employee e
            where m.team.id = :teamId and m.role in :roles
              and e.active = true and e.telegramChatId is not null
            """)
    List<TeamMembership> findLinkedByTeamIdAndRoleIn(@Param("teamId") Long teamId,
                                                     @Param("roles") Collection<Role> roles);

    long countByTeamId(Long teamId);

    long countByTeamIdAndRole(Long teamId, Role role);

    boolean existsByEmployeeIdAndTeamId(Long employeeId, Long teamId);
}
