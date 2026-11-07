package team.project.TviyHub.payment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team.project.TviyHub.payment.dto.PaymentResponseDto;
import team.project.TviyHub.payment.dto.PaymentUpdateDto;

import java.util.List;

@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(
        name = "Admin Payment Management",
        description = "Endpoints for administrators to track and manage user payment transactions"
)
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentMapper paymentMapper;

    @GetMapping
    @Operation(
            summary = "Get all payment transactions",
            description = "Retrieves a comprehensive list of all payments recorded in the system. [Admin Only]"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list of payments"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Access denied for non-admin accounts")
    })
    public ResponseEntity<List<PaymentResponseDto>> getAllPayments() {
        List<PaymentResponseDto> dtos = paymentService.getAllPayments().stream()
                .map(paymentMapper::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get payment transaction by ID",
            description = "Retrieves specific payment ledger details using its unique identifier. [Admin Only]"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved payment transaction details"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Access denied for non-admin accounts"),
            @ApiResponse(responseCode = "404", description = "Not Found - Payment transaction with specified ID does not exist")
    })
    public ResponseEntity<PaymentResponseDto> getPaymentById(@PathVariable Long id) {
        Payment payment = paymentService.getPaymentById(id);
        return ResponseEntity.ok(paymentMapper.toDto(payment));
    }

    @PutMapping("/{id}/status")
    @Operation(
            summary = "Update payment transaction status manually",
            description = "Allows administrators to manually switch payment states (e.g., confirming cash collection or issuing refunds). [Admin Only]"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment transaction status successfully updated"),
            @ApiResponse(responseCode = "400", description = "Bad Request - Invalid payload data or status transitions"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Access denied for non-admin accounts"),
            @ApiResponse(responseCode = "404", description = "Not Found - Payment transaction with specified ID does not exist")
    })
    public ResponseEntity<PaymentResponseDto> updatePaymentStatus(
            @PathVariable Long id,
            @Valid @RequestBody PaymentUpdateDto dto
            ) {
        Payment updatedPayment = paymentService.updatePaymentStatus(id, dto);
        return ResponseEntity.ok(paymentMapper.toDto(updatedPayment));
    }
}
