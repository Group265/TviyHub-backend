package team.project.TviyHub.workspace.dto;

import team.project.TviyHub.workspace.Workspace;

public record WorkspaceRequest(
        String name,
        Workspace.WorkspaceType type
) {}
