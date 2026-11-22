package team.project.TviyHub.workspace;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.booking.BookingRepository;
import team.project.TviyHub.exception.ConflictException;
import team.project.TviyHub.exception.ResourceNotFoundException;
import team.project.TviyHub.subscription.SubscriptionRepository;
import team.project.TviyHub.workspace.dto.WorkspaceRequest;

@Service
@RequiredArgsConstructor
public class WorkspaceService {
    private static final ZoneId KYIV_ZONE = ZoneId.of("Europe/Kyiv");

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMapper workspaceMapper;
    private final BookingRepository bookingRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Transactional(readOnly = true)
    public List<Workspace> getAllActiveWorkspaces() {
        return workspaceRepository.findByIsDeletedFalse();
    }

    @Transactional(readOnly = true)
    public Workspace getWorkspaceById(Long id) {
        return workspaceRepository.findById(id)
                .filter(w -> !w.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found or deleted"));
    }

    @Transactional
    public Workspace createWorkspace(WorkspaceRequest request) {
        Workspace workspace = workspaceMapper.toEntity(request);
        return workspaceRepository.save(workspace);
    }

    @Transactional
    public Workspace updateWorkspace(Long id, WorkspaceRequest request) {
        Workspace workspace = getWorkspaceById(id);
        workspaceMapper.updateEntityFromDto(request, workspace);
        return workspaceRepository.save(workspace);
    }

    @Transactional
    public void deleteWorkspace(Long id) {
        Workspace workspace = getWorkspaceById(id);
        LocalDate today = LocalDate.now(KYIV_ZONE);

        if (bookingRepository.existsFutureActiveBookings(id, today)) {
            throw new ConflictException("Cannot delete workspace: it has active or upcoming bookings");
        }
        if (subscriptionRepository.existsActiveSubscriptions(id, today)) {
            throw new ConflictException("Cannot delete workspace: it has an active or pending subscription");
        }
        workspace.setDeleted(true);
        workspaceRepository.save(workspace);
    }
}
