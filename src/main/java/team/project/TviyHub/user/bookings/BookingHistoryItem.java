package team.project.TviyHub.user.bookings;

import io.swagger.v3.oas.annotations.media.Schema;
import team.project.TviyHub.booking.Booking.BookingStatus;
import team.project.TviyHub.tariff.Tariff.BookingType;
import team.project.TviyHub.workspace.Workspace.WorkspaceType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "One booking in the history of the current user")
public record BookingHistoryItem(
		@Schema(example = "15") Long id,
		@Schema(description = "The day the place is booked for", example = "2026-11-20") LocalDate bookingDate,
		@Schema(description = "Start time; null for daily bookings", example = "10:00:00", nullable = true) LocalTime startTime,
		@Schema(description = "End time; null for daily bookings", example = "12:00:00", nullable = true) LocalTime endTime,
		@Schema(example = "HOURLY") BookingType bookingType,
		@Schema(example = "CONFIRMED") BookingStatus status,
		@Schema(example = "300.00") BigDecimal totalPrice,
		WorkspaceSummary workspace
) {

	@Schema(description = "The place that was booked")
	public record WorkspaceSummary(
			@Schema(example = "3") Long id,
			@Schema(example = "Desk 3") String name,
			@Schema(example = "DESK") WorkspaceType type
	) {
	}

	/** Used by the JPQL constructor expression in {@link MyBookingRepository}. */
	public BookingHistoryItem(Long id, LocalDate bookingDate, LocalTime startTime, LocalTime endTime,
							  BookingType bookingType, BookingStatus status, BigDecimal totalPrice,
							  Long workspaceId, String workspaceName, WorkspaceType workspaceType) {
		this(id, bookingDate, startTime, endTime, bookingType, status, totalPrice,
				new WorkspaceSummary(workspaceId, workspaceName, workspaceType));
	}
}
