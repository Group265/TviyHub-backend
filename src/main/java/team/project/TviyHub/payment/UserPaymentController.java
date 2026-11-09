package team.project.TviyHub.payment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import team.project.TviyHub.payment.dto.PaymentResponseDto;
import team.project.TviyHub.payment.dto.StripeSessionResponseDto;

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
            @RequestParam String cancelUrl
    ) {
        StripeSessionResponseDto sessionDto = paymentService.createStripeSession(id, successUrl, cancelUrl);
        return ResponseEntity.ok(sessionDto);
    }

    @GetMapping("/success")
    @Operation(summary = "Verify Stripe payment completion",
            description = "Callback invoked when client returns from Stripe")
    public ResponseEntity<PaymentResponseDto> verifyPayment(@RequestParam("session_id") String sessionId) {
        Payment payment = paymentService.verifyStripePayment(sessionId);
        return ResponseEntity.ok(paymentMapper.toDto(payment));
    }
}
