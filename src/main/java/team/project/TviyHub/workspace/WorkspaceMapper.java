package team.project.TviyHub.workspace;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import team.project.TviyHub.config.MapStructConfig;
import team.project.TviyHub.workspace.dto.WorkspaceRequest;
import team.project.TviyHub.workspace.dto.WorkspaceResponse;

@Mapper(config = MapStructConfig.class)
public interface WorkspaceMapper {

    WorkspaceResponse toDto(Workspace workspace);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    Workspace toEntity(WorkspaceRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateEntityFromDto(WorkspaceRequest request, @MappingTarget Workspace workspace);
}
