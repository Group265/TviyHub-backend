package team.project.TviyHub.closedday;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import team.project.TviyHub.closedday.dto.ClosedDayRequest;
import team.project.TviyHub.closedday.dto.ClosedDayResponse;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Closed Days Management", description = "Endpoints for managing hub holidays, repairs and non-working days")
public class ClosedDayController {

    private final ClosedDayService closedDayService;
    private final ClosedDayMapper closedDayMapper;

    @GetMapping("/closed-days")
    @Operation(
            summary = "Get all closed days",
            description = "Retrieve a list of all dates marked as closed by the administration to prevent system bookings."
    )
    public ResponseEntity<List<ClosedDayResponse>> getAll() {
        List<ClosedDayResponse> responses = closedDayService.getAllClosedDays().stream()
                .map(closedDayMapper::toDto)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/closed-days/check")
    @Operation(
            summary = "Check if date is closed",
            description = "Returns true if the specific date is unavailable for bookings due to repairs or holidays."
    )
    public ResponseEntity<Boolean> checkAvailability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(closedDayService.isDateClosed(date));
    }

    @PostMapping("/admin/closed-days")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Mark day as closed",
            description = "Block a specific date for all bookings with a valid reason. Restricted to administrators."
    )
    public ResponseEntity<ClosedDayResponse> create(@Valid @RequestBody ClosedDayRequest request) {
        ClosedDay closedDay = closedDayService.createClosedDay(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(closedDayMapper.toDto(closedDay));
    }

    @PutMapping("/admin/closed-days/{date}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Update closed day reason",
            description = "Modify the reason description for an already closed date. Restricted to administrators."
    )
    public ResponseEntity<ClosedDayResponse> update(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody ClosedDayRequest request) {
        ClosedDay closedDay = closedDayService.updateClosedDay(date, request);
        return ResponseEntity.ok(closedDayMapper.toDto(closedDay));
    }

    @DeleteMapping("/admin/closed-days/{date}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Remove day from closed list",
            description = "Unblock a previously closed date, making it active and available for system bookings. Restricted to administrators."
    )
    public ResponseEntity<Void> delete(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        closedDayService.deleteClosedDay(date);
        return ResponseEntity.noContent().build();
    }
}
