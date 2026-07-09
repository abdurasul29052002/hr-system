package uz.sonic.hr.team;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.sonic.hr.common.enums.JoinRequestStatus;

import java.util.List;
import java.util.Optional;

public interface TeamJoinRequestRepository extends JpaRepository<TeamJoinRequest, Long> {

    boolean existsByTeamIdAndEmployeeIdAndStatus(Long teamId, Long employeeId, JoinRequestStatus status);

    List<TeamJoinRequest> findAllByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    /** Removes every join request made by the given employee (used when they delete their account). */
    @Modifying
    @Query("delete from TeamJoinRequest r where r.employee.id = :employeeId")
    void deleteByEmployeeId(@Param("employeeId") Long employeeId);

    /**
     * Acquires a row-level write lock on the request so that two concurrent approve/reject decisions
     * (e.g. one from Telegram, one from the web) are serialized: the second decision blocks here until
     * the first commits, then re-reads the now non-PENDING status and is rejected. Root row only —
     * no join fetch — to avoid locking the referenced employee/team rows.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TeamJoinRequest r where r.id = :id")
    Optional<TeamJoinRequest> lockById(@Param("id") Long id);

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
