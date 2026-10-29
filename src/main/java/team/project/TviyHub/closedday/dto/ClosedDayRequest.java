package team.project.TviyHub.closedday.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ClosedDayRequest(
        @NotNull(message = "Date is required")
        LocalDate date,

        @NotBlank(message = "Reason for closing is required")
        String reason
) {}
