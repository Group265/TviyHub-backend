package team.project.TviyHub.event;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.event.dto.AttendEventRequest;
import team.project.TviyHub.event.dto.EventRequestResponse;
import team.project.TviyHub.event.dto.HostEventRequest;
import team.project.TviyHub.exception.BadRequestException;
import team.project.TviyHub.exception.ConflictException;
import team.project.TviyHub.exception.ForbiddenException;
import team.project.TviyHub.exception.ResourceNotFoundException;
import team.project.TviyHub.exception.UnauthorizedException;
import team.project.TviyHub.user.User;
import team.project.TviyHub.user.UserRepository;
import team.project.TviyHub.user.dto.PageResponse;
import team.project.TviyHub.verification.EmailService;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventRequestService {

	private static final int MAX_PAGE_SIZE = 100;

	private final EventRequestRepository requestRepository;
	private final EventRepository eventRepository;
	private final EventService eventService;
	private final EventScheduling scheduling;
	private final UserRepository userRepository;
	private final EmailService emailService;
	private final Clock clock;

	// ---- user side ----

	/** A user proposes to hold an event. */
	@Transactional
	public EventRequestResponse submitHost(Long userId, HostEventRequest body) {
		requireVerified(userId);
		scheduling.assertValidSlot(body.date(), body.startTime(), body.endTime());
		scheduling.assertDateAvailable(body.date(), null);

		EventRequest request = newRequest(userId, EventRequestType.HOST);
		request.setTitle(body.title().trim());
		request.setDescription(body.description().trim());
		request.setEventDate(body.date());
		request.setStartTime(body.startTime());
		request.setEndTime(body.endTime());
		request.setCapacity(body.capacity());
		request.setComment(trimToNull(body.comment()));
		return toResponse(requestRepository.saveAndFlush(request));
	}

	/** A user asks to take part in an existing event. */
	@Transactional
	public EventRequestResponse submitAttend(Long userId, Long eventId, AttendEventRequest body) {
		requireVerified(userId);
		Event event = eventService.find(eventId);
		if (event.getEventDate().isBefore(scheduling.today())) {
			throw new BadRequestException("The event has already taken place");
		}
		if (userId.equals(event.getHostUserId())) {
			throw new BadRequestException("You are the organizer of this event");
		}
		if (requestRepository.existsByEventIdAndUserIdAndTypeAndStatusIn(eventId, userId, EventRequestType.ATTEND,
				Set.of(EventRequestStatus.NEW, EventRequestStatus.APPROVED))) {
			throw new ConflictException("You already have an active request for this event");
		}

		EventRequest request = newRequest(userId, EventRequestType.ATTEND);
		request.setEventId(eventId);
		request.setComment(body == null ? null : trimToNull(body.comment()));
		return toResponse(requestRepository.saveAndFlush(request));
	}

	@Transactional(readOnly = true)
	public List<EventRequestResponse> myRequests(Long userId) {
		return requestRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toResponse).toList();
	}

	/** A user takes back their own request while it is still NEW. */
	@Transactional
	public void withdraw(Long userId, Long requestId) {
		EventRequest request = requestRepository.findById(requestId)
				.filter(r -> r.getUserId().equals(userId))
				.orElseThrow(() -> new ResourceNotFoundException("Request " + requestId + " not found"));
		if (request.getStatus() != EventRequestStatus.NEW) {
			throw new ConflictException("Only requests with status NEW can be withdrawn");
		}
		requestRepository.delete(request);
	}

	// ---- admin side ----

	@Transactional(readOnly = true)
	public PageResponse<EventRequestResponse> list(EventRequestStatus status, EventRequestType type, int page, int size) {
		Specification<EventRequest> spec = (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (status != null) {
				predicates.add(cb.equal(root.get("status"), status));
			}
			if (type != null) {
				predicates.add(cb.equal(root.get("type"), type));
			}
			return cb.and(predicates.toArray(new Predicate[0]));
		};
		PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
				Sort.by("createdAt").ascending());
		Page<EventRequestResponse> result = requestRepository.findAll(spec, pageRequest).map(this::toResponse);
		return PageResponse.from(result);
	}

	@Transactional
	public EventRequestResponse approve(Long adminId, Long requestId) {
		EventRequest request = findNew(requestId);
		if (request.getType() == EventRequestType.HOST) {
			approveHost(request);
		} else {
			approveAttend(request);
		}
		markReviewed(request, EventRequestStatus.APPROVED, adminId, null);
		EventRequest saved = requestRepository.saveAndFlush(request);
		notifyUser(saved, "approved");
		return toResponse(saved);
	}

	@Transactional
	public EventRequestResponse reject(Long adminId, Long requestId, String reason) {
		EventRequest request = findNew(requestId);
		markReviewed(request, EventRequestStatus.REJECTED, adminId, trimToNull(reason));
		EventRequest saved = requestRepository.saveAndFlush(request);
		notifyUser(saved, "rejected");
		return toResponse(saved);
	}

	private void approveHost(EventRequest request) {
		scheduling.assertValidSlot(request.getEventDate(), request.getStartTime(), request.getEndTime());
		scheduling.assertDateAvailable(request.getEventDate(), null);

		Event event = new Event();
		event.setTitle(request.getTitle());
		event.setDescription(request.getDescription());
		event.setEventDate(request.getEventDate());
		event.setStartTime(request.getStartTime());
		event.setEndTime(request.getEndTime());
		event.setCapacity(request.getCapacity());
		event.setHostUserId(request.getUserId());
		event.setCreatedAt(Instant.now(clock));
		request.setEventId(eventRepository.saveAndFlush(event).getId());
	}

	private void approveAttend(EventRequest request) {
		Event event = eventService.find(request.getEventId());
		if (event.getEventDate().isBefore(scheduling.today())) {
			throw new BadRequestException("The event has already taken place");
		}
		if (event.getCapacity() != null && eventService.approvedParticipants(event.getId()) >= event.getCapacity()) {
			throw new ConflictException("The event is full");
		}
	}

	private EventRequest findNew(Long requestId) {
		EventRequest request = requestRepository.findById(requestId)
				.orElseThrow(() -> new ResourceNotFoundException("Request " + requestId + " not found"));
		if (request.getStatus() != EventRequestStatus.NEW) {
			throw new ConflictException("The request has already been reviewed");
		}
		return request;
	}

	private void markReviewed(EventRequest request, EventRequestStatus status, Long adminId, String reviewComment) {
		request.setStatus(status);
		request.setReviewedAt(Instant.now(clock));
		request.setReviewedBy(adminId);
		request.setReviewComment(reviewComment);
	}

	/** A failed email must not undo the decision, so errors are only logged. */
	private void notifyUser(EventRequest request, String decision) {
		try {
			User user = userRepository.findById(request.getUserId()).orElse(null);
			if (user == null) {
				return;
			}
			String title = request.getType() == EventRequestType.HOST
					? request.getTitle()
					: eventService.find(request.getEventId()).getTitle();
			String text = "Your request for the event \"" + title + "\" was " + decision + "."
					+ (request.getReviewComment() != null ? "\nReason: " + request.getReviewComment() : "");
			emailService.send(user.getEmail(), "TviyHub: your event request was " + decision, text);
		} catch (MailException e) {
			log.warn("Could not send the event request notification", e);
		}
	}

	private EventRequest newRequest(Long userId, EventRequestType type) {
		EventRequest request = new EventRequest();
		request.setUserId(userId);
		request.setType(type);
		request.setStatus(EventRequestStatus.NEW);
		request.setCreatedAt(Instant.now(clock));
		return request;
	}

	private void requireVerified(Long userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new UnauthorizedException("User no longer exists"));
		if (!user.isEmailVerified()) {
			throw new ForbiddenException("Verify your email first");
		}
	}

	private EventRequestResponse toResponse(EventRequest request) {
		String title = request.getTitle();
		java.time.LocalDate date = request.getEventDate();
		if (request.getEventId() != null) {
			Event event = eventRepository.findById(request.getEventId()).orElse(null);
			if (event != null) {
				title = event.getTitle();
				date = event.getEventDate();
			}
		}
		return EventRequestResponse.from(request, eventService.userName(request.getUserId()), title, date);
	}

	private static String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
