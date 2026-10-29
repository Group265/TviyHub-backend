package team.project.TviyHub.tariff;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import team.project.TviyHub.config.MapStructConfig;
import team.project.TviyHub.tariff.dto.TariffRequest;
import team.project.TviyHub.tariff.dto.TariffResponse;

@Mapper(config = MapStructConfig.class)
public interface TariffMapper {

    TariffResponse toDto(Tariff tariff);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    Tariff toEntity(TariffRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateEntityFromDto(TariffRequest request, @MappingTarget Tariff tariff);
}
