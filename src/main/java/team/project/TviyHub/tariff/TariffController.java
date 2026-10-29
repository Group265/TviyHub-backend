package team.project.TviyHub.tariff;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;
import team.project.TviyHub.tariff.dto.TariffRequest;
import team.project.TviyHub.tariff.dto.TariffResponse;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Tariff Management", description = "Endpoints for managing workspace pricing and tariffs")
public class TariffController {

    private final TariffService tariffService;
    private final TariffMapper tariffMapper;

    @GetMapping("/tariffs")
    @Operation(
            summary = "Get public price list",
            description = "Retrieve all available active tariffs and prices for resources to render the public services view."
    )
    public ResponseEntity<List<TariffResponse>> getPriceList() {
        List<TariffResponse> responses = tariffService.getAllActiveTariffs().stream()
                .map(tariffMapper::toDto)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/tariffs/{id}")
    @Operation(
            summary = "Get tariff by ID",
            description = "Retrieve detailed information about a specific active pricing tariff by its unique ID."
    )
    public ResponseEntity<TariffResponse> getById(@PathVariable Long id) {
        Tariff tariff = tariffService.getTariffById(id);
        return ResponseEntity.ok(tariffMapper.toDto(tariff));
    }

    @PostMapping("/admin/tariffs")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Create a new tariff",
            description = "Add a new pricing tariff configuration for desks or meeting rooms. Restricted to administrators."
    )
    public ResponseEntity<TariffResponse> create(@Valid @RequestBody TariffRequest request) {
        Tariff tariff = tariffService.createTariff(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tariffMapper.toDto(tariff));
    }

    @PutMapping("/admin/tariffs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Update an existing tariff",
            description = "Modify the price or types of an existing tariff configuration by its unique ID. Restricted to administrators."
    )
    public ResponseEntity<TariffResponse> update(@PathVariable Long id, @Valid @RequestBody TariffRequest request) {
        Tariff tariff = tariffService.updateTariff(id, request);
        return ResponseEntity.ok(tariffMapper.toDto(tariff));
    }

    @DeleteMapping("/admin/tariffs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Delete a tariff",
            description = "Soft-delete a pricing tariff from the system active catalog. Restricted to administrators."
    )
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        tariffService.deleteTariff(id);
        return ResponseEntity.noContent().build();
    }
}
