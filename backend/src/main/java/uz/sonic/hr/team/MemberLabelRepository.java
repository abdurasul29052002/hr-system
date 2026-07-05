package uz.sonic.hr.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MemberLabelRepository extends JpaRepository<MemberLabel, Long> {

    List<MemberLabel> findAllByTeamIdOrderByNameAsc(Long teamId);

    List<MemberLabel> findAllByIdInAndTeamId(Collection<Long> ids, Long teamId);

    boolean existsByTeamIdAndNameIgnoreCase(Long teamId, String name);

    @Modifying
    @Query(value = "delete from membership_labels where label_id = :labelId", nativeQuery = true)
    void removeLabelFromMembers(@Param("labelId") Long labelId);
}
