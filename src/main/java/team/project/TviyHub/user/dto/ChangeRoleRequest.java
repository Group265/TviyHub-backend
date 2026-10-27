package team.project.TviyHub.user.dto;

import jakarta.validation.constraints.NotNull;
import team.project.TviyHub.user.Role;

public record ChangeRoleRequest(@NotNull Role role) {
}
