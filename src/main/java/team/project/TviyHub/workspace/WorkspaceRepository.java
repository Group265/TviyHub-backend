package team.project.TviyHub.workspace;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {

    List<Workspace> findByType(Workspace.WorkspaceType type);
}
