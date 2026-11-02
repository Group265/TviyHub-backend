package team.project.TviyHub.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A request to attend an event (ATTEND, points to an existing event) or to host one (HOST, holds the
 * proposed event; the event is created and linked when an admin approves it).
 */
@Entity
@Table(name = "event_requests")
@Getter
@Setter
@NoArgsConstructor
public class EventRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private EventRequestType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private EventRequestStatus status;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "event_id")
	private Long eventId;

	// Proposed event (HOST requests only)
	@Column(length = 150)
	private String title;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(name = "event_date")
	private LocalDate eventDate;

	@Column(name = "start_time")
	private LocalTime startTime;

	@Column(name = "end_time")
	private LocalTime endTime;

	private Integer capacity;

	@Column(length = 500)
	private String comment;

	@Column(name = "review_comment", length = 500)
	private String reviewComment;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "reviewed_at")
	private Instant reviewedAt;

	@Column(name = "reviewed_by")
	private Long reviewedBy;
}
