package team.project.TviyHub.closedday;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface ClosedDayRepository extends JpaRepository<ClosedDay, LocalDate> {

    boolean existsByDate(LocalDate date);
}
