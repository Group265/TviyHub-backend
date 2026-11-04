package team.project.TviyHub.subscription;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import team.project.TviyHub.config.MapStructConfig;
import team.project.TviyHub.subscription.dto.SubscriptionRequest;
import team.project.TviyHub.subscription.dto.SubscriptionResponse;

@Mapper(config = MapStructConfig.class)
public interface SubscriptionMapper {

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "workspace.id", target = "workspaceId")
    @Mapping(source = "payment.status", target = "paymentStatus")
    SubscriptionResponse toDto(Subscription subscription);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "workspace", ignore = true)
    @Mapping(target = "endDate", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "payment", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Subscription toEntity(SubscriptionRequest request);
}
