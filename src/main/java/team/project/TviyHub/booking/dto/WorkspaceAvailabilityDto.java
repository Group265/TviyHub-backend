package team.project.TviyHub.booking.dto;

import team.project.TviyHub.workspace.Workspace.WorkspaceType;

public record WorkspaceAvailabilityDto(
        Long id,
        String name,
        WorkspaceType type,
        boolean isAvailable
) {}
