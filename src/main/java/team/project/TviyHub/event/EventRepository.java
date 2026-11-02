package team.project.TviyHub.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

	boolean existsByEventDate(LocalDate eventDate);

	boolean existsByEventDateAndIdNot(LocalDate eventDate, Long id);

	@Query("select e from Event e where e.eventDate >= :from and e.eventDate <= :to order by e.eventDate asc")
	List<Event> findBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
