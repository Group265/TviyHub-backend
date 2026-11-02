package team.project.TviyHub.event;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import team.project.TviyHub.config.OpenApiConfig;
import team.project.TviyHub.event.dto.AdminEventRequest;
import team.project.TviyHub.event.dto.AdminEventUpdateRequest;
import team.project.TviyHub.event.dto.EventRequestResponse;
import team.project.TviyHub.event.dto.EventResponse;
import team.project.TviyHub.event.dto.ReviewEventRequest;
import team.project.TviyHub.user.User;
import team.project.TviyHub.user.dto.PageResponse;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin: events", description = "Review event requests and manage events (ADMIN role only)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@ApiResponse(responseCode = "401", description = "Authentication required")
@ApiResponse(responseCode = "403", description = "ADMIN role required")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEventController {

	private final EventService eventService;
	private final EventRequestService requestService;

	@GetMapping("/event-requests")
	@Operation(summary = "List event requests",
			description = "Filter by status (default NEW) and type. Oldest first, so the queue is handled in order.")
	public PageResponse<EventRequestResponse> list(
			@RequestParam(defaultValue = "NEW") EventRequestStatus status,
			@RequestParam(required = false) EventRequestType type,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return requestService.list(status, type, page, size);
	}

	@PostMapping("/event-requests/{id}/approve")
	@Operation(summary = "Approve a request",
			description = "HOST: creates the event with the requester as organizer. ATTEND: adds a participant if there are free seats. "
					+ "The user is notified by email.")
	@ApiResponse(responseCode = "404", description = "Request not found")
	@ApiResponse(responseCode = "409", description = "Already reviewed, the date is no longer free, or the event is full")
	public EventRequestResponse approve(@AuthenticationPrincipal User admin, @PathVariable Long id) {
		return requestService.approve(admin.getId(), id);
	}

	@PostMapping("/event-requests/{id}/reject")
	@Operation(summary = "Reject a request", description = "The optional reason is sent to the user by email.")
	@ApiResponse(responseCode = "404", description = "Request not found")
	@ApiResponse(responseCode = "409", description = "Already reviewed")
	public EventRequestResponse reject(@AuthenticationPrincipal User admin, @PathVariable Long id,
									   @Valid @RequestBody(required = false) ReviewEventRequest request) {
		return requestService.reject(admin.getId(), id, request == null ? null : request.reason());
	}

	@PostMapping("/events")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create an event directly", description = "Optionally name an organizer by user id.")
	@ApiResponse(responseCode = "400", description = "Validation failed, not a weekend or the date is in the past")
	@ApiResponse(responseCode = "409", description = "The date is taken, closed or has confirmed bookings")
	public EventResponse create(@Valid @RequestBody AdminEventRequest request) {
		return eventService.create(request);
	}

	@PatchMapping("/events/{id}")
	@Operation(summary = "Update an event", description = "Only the provided fields are changed.")
	@ApiResponse(responseCode = "404", description = "Event not found")
	@ApiResponse(responseCode = "409", description = "The new date is not free, or capacity is below the approved participants")
	public EventResponse update(@PathVariable Long id, @Valid @RequestBody AdminEventUpdateRequest request) {
		return eventService.update(id, request);
	}

	@DeleteMapping("/events/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Delete an event", description = "Also deletes the requests that point to it.")
	@ApiResponse(responseCode = "404", description = "Event not found")
	public void delete(@PathVariable Long id) {
		eventService.delete(id);
	}

	@GetMapping("/events/{id}/requests")
	@Operation(summary = "Requests to attend an event")
	@ApiResponse(responseCode = "404", description = "Event not found")
	public List<EventRequestResponse> participantRequests(@PathVariable Long id) {
		return eventService.participantRequests(id);
	}
}
