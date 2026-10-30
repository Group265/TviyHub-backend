package team.project.TviyHub.booking;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import team.project.TviyHub.booking.dto.BookingRequestDto;
import team.project.TviyHub.booking.dto.BookingResponseDto;
import team.project.TviyHub.user.User;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings Management", description = "Endpoints for creating and managing hourly and daily bookings")
public class BookingController {

    private final BookingService bookingService;

    @GetMapping
    @Operation(
            summary = "Get list of bookings",
            description = "Clients (USER) receive only their own bookings. Administrators (ADMIN) receive all bookings in the system, with an optional filter by userId."
    )
    public ResponseEntity<List<BookingResponseDto>> getBookings(
            @RequestParam(required = false) Long userId,
            @AuthenticationPrincipal User currentUser
    ) {
        List<BookingResponseDto> responses = bookingService.getAllBookings(userId, currentUser);
        return ResponseEntity.ok(responses);
    }

    @PostMapping
    @Operation(
            summary = "Create a new booking",
            description = "Allows logged-in clients (USER) to book a desk or meeting room during working hours (09:00-19:00). " +
                    "Administrators (ADMIN) can book for any time. Validates overlaps with active subscriptions and existing bookings."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Booking created successfully",
                    content = @Content(schema = @Schema(implementation = BookingResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid time slot, missing arguments, or weekend booking attempted by a client",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "User is not authenticated",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "Conflict: Selected time slot overlaps with an existing booking, active subscription, or the hub is closed",
                    content = @Content)
    })
    public ResponseEntity<BookingResponseDto> createBooking(
            @Valid @RequestBody BookingRequestDto requestDto,
            @AuthenticationPrincipal User currentUser
    ) {
        BookingResponseDto response = bookingService.createBooking(requestDto, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}/cancel")
    @Operation(
            summary = "Cancel a booking",
            description = "Allows a client to cancel their own booking (at least 2 hours before start for hourly, 1 day before for daily). " +
                    "Administrators can cancel any booking at any time. Updates payment status to REFUND_PENDING if already paid."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Booking cancelled successfully",
                    content = @Content(schema = @Schema(implementation = BookingResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Booking is already cancelled or cancellation deadline has passed",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "User is not authenticated",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden: Client tries to cancel someone else's booking",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Booking with the specified ID not found",
                    content = @Content)
    })
    public ResponseEntity<BookingResponseDto> cancelBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser
    ) {
        BookingResponseDto response = bookingService.cancelBooking(id, currentUser);
        return ResponseEntity.ok(response);
    }
}
