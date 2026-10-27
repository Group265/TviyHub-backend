package team.project.TviyHub.tariff;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import team.project.TviyHub.workspace.Workspace;

@Repository
public interface TariffRepository extends JpaRepository<Tariff, Long> {

    Optional<Tariff> findByWorkspaceTypeAndBookingType(
            Workspace.WorkspaceType workspaceType,
            Tariff.BookingType bookingType
    );
}
