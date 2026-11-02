package team.project.TviyHub.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** An event that rents the whole hub for one weekend day. */
@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
public class Event {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 150)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String description;

	@Column(name = "event_date", nullable = false, unique = true)
	private LocalDate eventDate;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	/** Number of seats for participants; null means unlimited. */
	private Integer capacity;

	/** The organizer; null for events created by an admin without an organizer. */
	@Column(name = "host_user_id")
	private Long hostUserId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
}
