package team.project.TviyHub.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import team.project.TviyHub.booking.Booking;
import team.project.TviyHub.subscription.Subscription;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @OneToOne(mappedBy = "payment", fetch = FetchType.LAZY)
    private Booking booking;

    @OneToOne(mappedBy = "payment", fetch = FetchType.LAZY)
    private Subscription subscription;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    @Column(name = "external_transaction_id", unique = true, length = 100)
    private String externalTransactionId;

    public enum PaymentStatus {
        UNPAID,
        PAID,
        REFUNDED
    }

    public enum PaymentMethod {
        CASH,
        CARD,       // terminal
        STRIPE      // online payment
    }
}
