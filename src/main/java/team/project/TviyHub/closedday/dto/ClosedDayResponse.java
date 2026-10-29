package team.project.TviyHub.closedday.dto;

import java.time.LocalDate;

public record ClosedDayResponse(
   LocalDate date,
   String reason
) {}
