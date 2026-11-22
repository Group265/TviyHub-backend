package team.project.TviyHub.closedday;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import team.project.TviyHub.closedday.dto.ClosedDayRequest;
import team.project.TviyHub.closedday.dto.ClosedDayResponse;
import team.project.TviyHub.config.MapStructConfig;

@Mapper(config = MapStructConfig.class)
public interface ClosedDayMapper {

    ClosedDayResponse toDto(ClosedDay closedDay);

    ClosedDay toEntity(ClosedDayRequest request);

    @Mapping(target = "date", ignore = true)
    void updateEntityFromDto(ClosedDayRequest request, @MappingTarget ClosedDay closedDay);
}
