package team.project.TviyHub.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import team.project.TviyHub.workspace.Workspace;

public record WorkspaceRequest(
        @NotBlank(message = "Name cannot be blank")
        String name,

        @NotNull(message = "Workspace type cannot be blank")
        Workspace.WorkspaceType type
) {}
