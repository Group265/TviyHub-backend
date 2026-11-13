package team.project.TviyHub.event;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import team.project.TviyHub.config.OpenApiConfig;
import team.project.TviyHub.event.dto.AttendEventRequest;
import team.project.TviyHub.event.dto.EventRequestResponse;
import team.project.TviyHub.event.dto.EventResponse;
import team.project.TviyHub.event.dto.HostEventRequest;
import team.project.TviyHub.user.User;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Events", description = "Event calendar, event card and requests to attend or host an event")
public class EventController {

	private final EventService eventService;
	private final EventRequestService requestService;

	@GetMapping("/events")
	@Operation(summary = "Event calendar (public)",
			description = "UPCOMING (default) returns future events, nearest first; PAST returns past events, newest first. "
					+ "from and to narrow the date range (format yyyy-MM-dd).")
	@ApiResponse(responseCode = "400", description = "Unknown period or invalid date")
	public List<EventResponse> list(
			@RequestParam(defaultValue = "UPCOMING") EventService.Period period,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return eventService.list(period, from, to);
	}

	@GetMapping("/events/{id}")
	@Operation(summary = "Event card (public)",
			description = "Includes the organizer, the number of approved participants and the free seats.")
	@ApiResponse(responseCode = "404", description = "Event not found")
	public EventResponse get(@PathVariable Long id) {
		return eventService.get(id);
	}

	@PostMapping("/events/{id}/attend-requests")
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@Operation(summary = "Ask to take part in an event",
			description = "Requires a verified email. Created with status NEW; an admin approves or rejects it.")
	@ApiResponse(responseCode = "400", description = "Validation failed, the event has passed or you are its organizer")
	@ApiResponse(responseCode = "401", description = "Authentication required")
	@ApiResponse(responseCode = "403", description = "Email is not verified")
	@ApiResponse(responseCode = "404", description = "Event not found")
	@ApiResponse(responseCode = "409", description = "You already have an active request for this event")
	public EventRequestResponse attend(@AuthenticationPrincipal User user, @PathVariable Long id,
									   @Valid @RequestBody(required = false) AttendEventRequest request) {
		return requestService.submitAttend(user.getId(), id, request);
	}

	@PostMapping("/events/host-requests")
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@Operation(summary = "Propose an event",
			description = "The hub is rented as a whole, so the date must be a free Saturday or Sunday. "
					+ "Requires a verified email. When an admin approves it, the event is created with you as organizer.")
	@ApiResponse(responseCode = "400", description = "Validation failed, not a weekend or the date is in the past")
	@ApiResponse(responseCode = "401", description = "Authentication required")
	@ApiResponse(responseCode = "403", description = "Email is not verified")
	@ApiResponse(responseCode = "409", description = "The date is taken, closed or has confirmed bookings")
	public EventRequestResponse host(@AuthenticationPrincipal User user, @Valid @RequestBody HostEventRequest request) {
		return requestService.submitHost(user.getId(), request);
	}

	@GetMapping("/users/me/event-requests")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@Operation(summary = "My event requests with their statuses", description = "Newest first.")
	@ApiResponse(responseCode = "401", description = "Authentication required")
	public List<EventRequestResponse> mine(@AuthenticationPrincipal User user) {
		return requestService.myRequests(user.getId());
	}

	@DeleteMapping("/users/me/event-requests/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@Operation(summary = "Withdraw my request", description = "Only requests with status NEW can be withdrawn.")
	@ApiResponse(responseCode = "401", description = "Authentication required")
	@ApiResponse(responseCode = "404", description = "Request not found")
	@ApiResponse(responseCode = "409", description = "The request has already been reviewed")
	public void withdraw(@AuthenticationPrincipal User user, @PathVariable Long id) {
		requestService.withdraw(user.getId(), id);
	}
}
