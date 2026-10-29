package team.project.TviyHub.tariff.dto;

import java.math.BigDecimal;

public record TariffResponse(
   Long id,
   String workspaceType,
   String bookingType,
   BigDecimal price
) {}
