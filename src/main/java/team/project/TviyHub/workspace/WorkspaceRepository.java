package team.project.TviyHub.workspace;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {

    List<Workspace> findByType(Workspace.WorkspaceType type);

    List<Workspace> findByIsDeletedFalse();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Workspace w WHERE w.id = :id AND w.isDeleted = false")
    Optional<Workspace> findByIdForUpdate(@Param("id") Long id);
}
