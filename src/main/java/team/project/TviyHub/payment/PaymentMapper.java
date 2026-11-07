package team.project.TviyHub.payment;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import team.project.TviyHub.config.MapStructConfig;
import team.project.TviyHub.payment.dto.PaymentResponseDto;

@Mapper(config = MapStructConfig.class)
public interface PaymentMapper {

    @Mapping(target = "bookingId", source = "payment.booking.id")
    @Mapping(target = "subscriptionId", source = "payment.subscription.id")
    PaymentResponseDto toDto(Payment payment);
}
