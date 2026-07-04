package uz.sonic.hr.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.sonic.hr.entity.Tag;

import java.util.Collection;
import java.util.List;

public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findAllByTeamIdOrderByNameAsc(Long teamId);

    List<Tag> findAllByIdInAndTeamId(Collection<Long> ids, Long teamId);

    boolean existsByTeamIdAndNameIgnoreCase(Long teamId, String name);

    @Modifying
    @Query(value = "delete from task_tags where tag_id = :tagId", nativeQuery = true)
    void removeTagFromTasks(@Param("tagId") Long tagId);
}
