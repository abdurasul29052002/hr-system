package uz.sonic.hr.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.sonic.hr.common.enums.Role;
import uz.sonic.hr.team.TeamMembership;

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

    /** Active members of a team holding any of the given roles (for in-app notifications; no Telegram filter). */
    @Query("""
            select m from TeamMembership m join fetch m.employee e
            where m.team.id = :teamId and m.role in :roles and e.active = true
            """)
    List<TeamMembership> findActiveByTeamIdAndRoleIn(@Param("teamId") Long teamId,
                                                     @Param("roles") Collection<Role> roles);

    long countByTeamId(Long teamId);

    long countByTeamIdAndRole(Long teamId, Role role);

    boolean existsByEmployeeIdAndTeamId(Long employeeId, Long teamId);
}
