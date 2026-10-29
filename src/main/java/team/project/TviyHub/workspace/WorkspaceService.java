package team.project.TviyHub.workspace;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.exception.BusinessException;
import team.project.TviyHub.workspace.dto.WorkspaceRequest;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMapper workspaceMapper;

    @Transactional(readOnly = true)
    public List<Workspace> getAllActiveWorkspaces() {
        return workspaceRepository.findByIsDeletedFalse();
    }

    @Transactional(readOnly = true)
    public Workspace getWorkspaceById(Long id) {
        return workspaceRepository.findById(id)
                .filter(w -> !w.isDeleted())
                .orElseThrow(() -> new BusinessException("Workspace not found or deleted"));
    }

    @Transactional
    public Workspace createWorkspace(WorkspaceRequest request) {
        Workspace workspace = workspaceMapper.toEntity(request);
        return workspaceRepository.save(workspace);
    }

    @Transactional
    public Workspace updateWorkspace(Long id, WorkspaceRequest request) {
        Workspace workspace = getWorkspaceById(id);
        workspaceMapper.updateEntityFromDto(request, workspace);
        return workspaceRepository.save(workspace);
    }

    @Transactional
    public void deleteWorkspace(Long id) {
        Workspace workspace = getWorkspaceById(id);
        workspace.setDeleted(true);
        workspaceRepository.save(workspace);
    }
}
