package team.project.TviyHub.subscription;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import team.project.TviyHub.subscription.dto.SubscriptionRequest;
import team.project.TviyHub.subscription.dto.SubscriptionResponse;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(
        name = "Subscription Management",
        description = "Endpoints for managing client workspace subscriptions and holds"
)
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final SubscriptionMapper subscriptionMapper;

    @PostMapping("/subscriptions")
    @PreAuthorize("hasRole('USER')")
    @Operation(
            summary = "Create a new subscription (Hold desk)",
            description = "Creates a temporary hold for a specific DESK with status PENDING_PAYMENT. The hold lasts for 24 hours."
    )
    public ResponseEntity<SubscriptionResponse> create(
            @Valid @RequestBody SubscriptionRequest request,
            @AuthenticationPrincipal(expression = "id") Long userId
            ) {
        Subscription subscription = subscriptionService.createSubscription(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(subscriptionMapper.toDto(subscription));
    }

    @GetMapping("/subscriptions/my")
    @PreAuthorize("hasRole('USER')")
    @Operation(
            summary = "Get active user subscriptions",
            description = "Retrieve all subscriptions and history belonging strictly to the currently authenticated user."
    )
    public ResponseEntity<List<SubscriptionResponse>> getMySubscriptions(
            @AuthenticationPrincipal(expression = "id") Long userId) {

        List<SubscriptionResponse> responses = subscriptionService.getUserSubscriptions(userId).stream()
                .map(subscriptionMapper::toDto)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/admin/subscriptions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get all subscriptions list (Admin)",
            description = "Retrieve a global list of all user subscriptions in the system. Restricted to administrators."
    )
    public ResponseEntity<List<SubscriptionResponse>> getAll() {
        List<SubscriptionResponse> responses = subscriptionService.getAllSubscriptions().stream()
                .map(subscriptionMapper::toDto)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/admin/subscriptions/{id}/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Confirm payment (Admin)",
            description = "Manually confirm that the client has paid for the subscription. Changes status to ACTIVE. Restricted to administrators."
    )
    public ResponseEntity<SubscriptionResponse> confirmPayment(@PathVariable Long id) {
        Subscription subscription = subscriptionService.confirmPayment(id);
        return ResponseEntity.ok(subscriptionMapper.toDto(subscription));
    }

    @DeleteMapping("/admin/subscriptions/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Cancel subscription (Admin)",
            description = "Manually cancel an active or pending subscription. Changes payment status to REFUNDED if it was paid. Restricted to administrators."
    )
    public ResponseEntity<SubscriptionResponse> cancel(@PathVariable Long id) {
        Subscription subscription = subscriptionService.cancelSubscription(id);
        return ResponseEntity.ok(subscriptionMapper.toDto(subscription));
    }

    @PutMapping("/admin/subscriptions/{id}/extend")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Extend subscription (Admin)",
            description = "Manually extend an ACTIVE subscription by a specific number of days (e.g., due to hub repairs). Validates potential conflicts with upcoming subscriptions or daily/hourly bookings."
    )
    public ResponseEntity<SubscriptionResponse> extend(
            @PathVariable Long id,
            @RequestParam int days
    ) {
        Subscription subscription = subscriptionService.extendSubscription(id, days);
        return ResponseEntity.ok(subscriptionMapper.toDto(subscription));
    }
}

