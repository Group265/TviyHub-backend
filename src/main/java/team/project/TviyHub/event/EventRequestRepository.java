package team.project.TviyHub.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

public interface EventRequestRepository extends JpaRepository<EventRequest, Long>, JpaSpecificationExecutor<EventRequest> {

	long countByEventIdAndTypeAndStatus(Long eventId, EventRequestType type, EventRequestStatus status);

	boolean existsByEventIdAndUserIdAndTypeAndStatusIn(Long eventId, Long userId, EventRequestType type,
													   Collection<EventRequestStatus> statuses);

	List<EventRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

	List<EventRequest> findByEventIdAndTypeOrderByCreatedAtAsc(Long eventId, EventRequestType type);
}
