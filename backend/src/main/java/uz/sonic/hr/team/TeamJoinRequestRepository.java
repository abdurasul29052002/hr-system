package uz.sonic.hr.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.sonic.hr.common.enums.JoinRequestStatus;

import java.util.List;
import java.util.Optional;

public interface TeamJoinRequestRepository extends JpaRepository<TeamJoinRequest, Long> {

    boolean existsByTeamIdAndEmployeeIdAndStatus(Long teamId, Long employeeId, JoinRequestStatus status);

    List<TeamJoinRequest> findAllByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    @Query("""
            select r from TeamJoinRequest r
            join fetch r.employee e
            join fetch r.team t
            where r.team.id = :teamId and r.status = :status
            order by r.createdAt desc
            """)
    List<TeamJoinRequest> findAllByTeamIdAndStatusWithEmployee(@Param("teamId") Long teamId,
                                                               @Param("status") JoinRequestStatus status);

    @Query("""
            select r from TeamJoinRequest r
            join fetch r.employee e
            join fetch r.team t
            where r.id = :id
            """)
    Optional<TeamJoinRequest> findWithEmployeeAndTeamById(@Param("id") Long id);
}
