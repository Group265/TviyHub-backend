package team.project.TviyHub.payment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import team.project.TviyHub.payment.dto.BatchStripeSessionRequestDto;
import team.project.TviyHub.payment.dto.PaymentResponseDto;
import team.project.TviyHub.payment.dto.StripeSessionResponseDto;
import team.project.TviyHub.user.User;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
@Tag(
        name = "Client Payment Management",
        description = "Endpoints for users to process Stripe online payments"
)
public class UserPaymentController {

    private final PaymentService paymentService;
    private final PaymentMapper paymentMapper;

    @PostMapping("/{id}/stripe-session")
    @Operation(summary = "Create Stripe checkout session",
            description = "Generates a Stripe link for a specific payment ID")
    public ResponseEntity<StripeSessionResponseDto> createStripeSession(
            @PathVariable Long id,
            @RequestParam String successUrl,
            @RequestParam String cancelUrl,
            @AuthenticationPrincipal User currentUser
    ) {
        StripeSessionResponseDto sessionDto = paymentService.createStripeSession(
                id, successUrl, cancelUrl, currentUser.getId()
        );
        return ResponseEntity.ok(sessionDto);
    }

    @PostMapping("/stripe-session/batch")
    @Operation(summary = "Create Stripe checkout session for multiple payments",
            description = "Generates a single Stripe checkout link for one or more payment IDs")
    public ResponseEntity<StripeSessionResponseDto> createBatchStripeSession(
            @Valid @RequestBody BatchStripeSessionRequestDto requestDto,
            @AuthenticationPrincipal User currentUser
    ) {
        StripeSessionResponseDto sessionDto = paymentService.createBatchStripeSession(
                requestDto.paymentIds(),
                requestDto.successUrl(),
                requestDto.cancelUrl(),
                currentUser.getId()
        );
        return ResponseEntity.ok(sessionDto);
    }

    @GetMapping("/success")
    @Operation(summary = "Verify Stripe payment completion",
            description = "Callback invoked when client returns from Stripe")
    public ResponseEntity<List<PaymentResponseDto>> verifyPayment(@RequestParam("session_id") String sessionId) {
        List<PaymentResponseDto> dtos = paymentService.verifyStripePayment(sessionId).stream()
                .map(paymentMapper::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }
}
