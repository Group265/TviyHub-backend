package team.project.TviyHub.workspace;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import team.project.TviyHub.workspace.dto.WorkspaceRequest;
import team.project.TviyHub.workspace.dto.WorkspaceResponse;

@Tag(name = "Workspace Management", description = "Endpoints for managing coworking desks and meeting rooms")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final WorkspaceMapper workspaceMapper;

    @GetMapping("/workspaces")
    @Operation(
            summary = "Get hall layout",
            description = "Retrieve a full list of all active workspaces (desks and meeting rooms) for the public hall layout view."
    )
    public ResponseEntity<List<WorkspaceResponse>> getHallLayout() {
        List<WorkspaceResponse> responses = workspaceService.getAllActiveWorkspaces().stream()
                .map(workspaceMapper::toDto)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/workspaces/{id}")
    @Operation(
            summary = "Get workspace by ID",
            description = "Retrieve detailed information about a specific active workspace using its unique identifier."
    )
    public ResponseEntity<WorkspaceResponse> getById(@PathVariable Long id) {
        Workspace w = workspaceService.getWorkspaceById(id);
        return ResponseEntity.ok(workspaceMapper.toDto(w));
    }

    @PostMapping("/admin/workspaces")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Create a new workspace",
            description = "Add a new workspace (DESK or MEETING_ROOM) to the hub. Restricted to administrators."
    )
    public ResponseEntity<WorkspaceResponse> create(@RequestBody WorkspaceRequest request) {
        Workspace w = workspaceService.createWorkspace(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workspaceMapper.toDto(w));
    }

    @PutMapping("/admin/workspaces/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Update an existing workspace",
            description = "Modify the name or resource type of an existing workspace by its ID. Restricted to administrators."
    )
    public ResponseEntity<WorkspaceResponse> update(@PathVariable Long id, @RequestBody WorkspaceRequest request) {
        Workspace w = workspaceService.updateWorkspace(id, request);
        return ResponseEntity.ok(workspaceMapper.toDto(w));
    }

    @DeleteMapping("/admin/workspaces/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Delete a workspace",
            description = "Soft-delete a workspace from the system so it no longer appears in active layouts. Restricted to administrators."
    )
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        workspaceService.deleteWorkspace(id);
        return ResponseEntity.noContent().build();
    }
}
