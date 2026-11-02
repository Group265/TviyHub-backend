package team.project.TviyHub.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.event.dto.AdminEventRequest;
import team.project.TviyHub.event.dto.AdminEventUpdateRequest;
import team.project.TviyHub.event.dto.EventRequestResponse;
import team.project.TviyHub.event.dto.EventResponse;
import team.project.TviyHub.exception.ConflictException;
import team.project.TviyHub.exception.ResourceNotFoundException;
import team.project.TviyHub.user.User;
import team.project.TviyHub.user.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {

	public enum Period { UPCOMING, PAST }

	// Open-ended bounds for the date range query
	private static final LocalDate MIN_DATE = LocalDate.of(1900, 1, 1);
	private static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 31);

	private final EventRepository eventRepository;
	private final EventRequestRepository requestRepository;
	private final UserRepository userRepository;
	private final EventScheduling scheduling;
	private final Clock clock;

	/** Calendar: upcoming events by date ascending, or past events, newest first. */
	@Transactional(readOnly = true)
	public List<EventResponse> list(Period period, LocalDate from, LocalDate to) {
		LocalDate today = scheduling.today();
		LocalDate lower = from != null ? from : MIN_DATE;
		LocalDate upper = to != null ? to : MAX_DATE;
		if (period == Period.PAST) {
			LocalDate yesterday = today.minusDays(1);
			upper = upper.isAfter(yesterday) ? yesterday : upper;
		} else if (lower.isBefore(today)) {
			lower = today;
		}
		List<Event> events = new ArrayList<>(eventRepository.findBetween(lower, upper));
		if (period == Period.PAST) {
			Collections.reverse(events);
		}
		return events.stream().map(this::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public EventResponse get(Long id) {
		return toResponse(find(id));
	}

	@Transactional
	public EventResponse create(AdminEventRequest request) {
		scheduling.assertValidSlot(request.date(), request.startTime(), request.endTime());
		scheduling.assertDateAvailable(request.date(), null);
		if (request.hostUserId() != null && !userRepository.existsById(request.hostUserId())) {
			throw new ResourceNotFoundException("User " + request.hostUserId() + " not found");
		}
		Event event = new Event();
		event.setTitle(request.title().trim());
		event.setDescription(request.description().trim());
		event.setEventDate(request.date());
		event.setStartTime(request.startTime());
		event.setEndTime(request.endTime());
		event.setCapacity(request.capacity());
		event.setHostUserId(request.hostUserId());
		event.setCreatedAt(Instant.now(clock));
		return toResponse(eventRepository.saveAndFlush(event));
	}

	@Transactional
	public EventResponse update(Long id, AdminEventUpdateRequest request) {
		Event event = find(id);
		if (request.title() != null) {
			event.setTitle(request.title().trim());
		}
		if (request.description() != null) {
			event.setDescription(request.description().trim());
		}
		boolean scheduleChanged = request.date() != null || request.startTime() != null || request.endTime() != null;
		if (scheduleChanged) {
			var date = request.date() != null ? request.date() : event.getEventDate();
			var start = request.startTime() != null ? request.startTime() : event.getStartTime();
			var end = request.endTime() != null ? request.endTime() : event.getEndTime();
			scheduling.assertValidSlot(date, start, end);
			if (!date.equals(event.getEventDate())) {
				scheduling.assertDateAvailable(date, id);
			}
			event.setEventDate(date);
			event.setStartTime(start);
			event.setEndTime(end);
		}
		if (request.capacity() != null) {
			long approved = approvedParticipants(id);
			if (request.capacity() < approved) {
				throw new ConflictException("Capacity cannot be lower than the " + approved + " approved participants");
			}
			event.setCapacity(request.capacity());
		}
		return toResponse(eventRepository.saveAndFlush(event));
	}

	/** Deleting an event also deletes the requests that point to it. */
	@Transactional
	public void delete(Long id) {
		eventRepository.delete(find(id));
	}

	@Transactional(readOnly = true)
	public List<EventRequestResponse> participantRequests(Long eventId) {
		Event event = find(eventId);
		return requestRepository.findByEventIdAndTypeOrderByCreatedAtAsc(eventId, EventRequestType.ATTEND).stream()
				.map(r -> EventRequestResponse.from(r, userName(r.getUserId()), event.getTitle(), event.getEventDate()))
				.toList();
	}

	Event find(Long id) {
		return eventRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Event " + id + " not found"));
	}

	long approvedParticipants(Long eventId) {
		return requestRepository.countByEventIdAndTypeAndStatus(
				eventId, EventRequestType.ATTEND, EventRequestStatus.APPROVED);
	}

	String userName(Long userId) {
		return userId == null ? null : userRepository.findById(userId).map(User::getName).orElse(null);
	}

	private EventResponse toResponse(Event event) {
		boolean past = event.getEventDate().isBefore(scheduling.today());
		return EventResponse.from(event, userName(event.getHostUserId()), approvedParticipants(event.getId()), past);
	}
}
