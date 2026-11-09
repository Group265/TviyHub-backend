package team.project.TviyHub.payment;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.booking.Booking;
import team.project.TviyHub.exception.ResourceNotFoundException;
import team.project.TviyHub.payment.dto.PaymentUpdateDto;
import team.project.TviyHub.payment.dto.StripeSessionResponseDto;
import team.project.TviyHub.subscription.Subscription;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final StripeClient stripeClient;

    @Transactional(readOnly = true)
    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Payment getPaymentById(Long id) {
        return paymentRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Payment transaction not found with id: " + id));
    }

    @Transactional
    public Payment updatePaymentStatus(Long id, PaymentUpdateDto dto) {
        Payment payment = getPaymentById(id);

        return updatePaymentFields(payment, dto.status(),dto.paymentMethod());
    }

    @Transactional
    public StripeSessionResponseDto createStripeSession(Long paymentId, String successUrl, String cancelUrl) {
        Payment payment = getPaymentById(paymentId);

        if (payment.getStatus() == Payment.PaymentStatus.PAID) {
            throw new IllegalStateException("Payment is already paid.");
        }

        long stripeAmount = payment.getAmount().multiply(new BigDecimal(100)).longValue();

        String productName;
        if (payment.getBooking() != null) {
            productName = "Booking workspace #" + payment.getBooking().getId();
        } else if (payment.getSubscription() != null) {
            productName = "Subscription workspace #" + payment.getSubscription().getId();
        } else {
            productName = "Hub service payment #" + payment.getId();
        }

        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(cancelUrl)
                    .setCurrency("uah")
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(1L)
                                    .setPriceData(
                                            SessionCreateParams.LineItem.PriceData.builder()
                                                    .setCurrency("uah")
                                                    .setUnitAmount(stripeAmount)
                                                    .setProductData(
                                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                    .setName(productName)
                                                                    .build()
                                                    )
                                                    .build()
                                    )
                                    .build()
                    )
                    .build();
            Session session = stripeClient.checkout().sessions().create(params);
            payment.setExternalTransactionId(session.getId());
            payment.setPaymentMethod(Payment.PaymentMethod.STRIPE);
            paymentRepository.save(payment);

            return new StripeSessionResponseDto(session.getUrl(), session.getId());
        } catch (StripeException e) {
            throw new RuntimeException("Unable to create Stripe payment session", e);
        }
    }

    @Transactional
    public Payment verifyStripePayment(String sessionId) {
        Payment payment = paymentRepository.findByExternalTransactionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment with Stripe session ID not found: " + sessionId));

        try {
            Session session = stripeClient.checkout().sessions().retrieve(sessionId);

            if ("paid".equals(session.getPaymentStatus())) {
                return updatePaymentFields(payment, Payment.PaymentStatus.PAID, Payment.PaymentMethod.STRIPE);
            }
            throw new IllegalStateException("Stripe payment was not completed successfully. Status: " + session.getPaymentStatus());
        } catch (StripeException e) {
            throw new RuntimeException("Error verifying Stripe session status", e);
        }
    }

    private Payment updatePaymentFields(Payment payment, Payment.PaymentStatus status, Payment.PaymentMethod method) {
        payment.setStatus(status);
        payment.setPaymentMethod(method);

        if (status == Payment.PaymentStatus.PAID) {
            payment.setPaidAt(LocalDateTime.now());

            if (payment.getSubscription() != null) {
                payment.getSubscription().setStatus(Subscription.SubscriptionStatus.ACTIVE);
            }

            if (payment.getBooking() != null) {
                payment.getBooking().setStatus(Booking.BookingStatus.CONFIRMED);
            }
        } else if (status == Payment.PaymentStatus.UNPAID) {
            payment.setPaidAt(null);
        }
        return paymentRepository.save(payment);
    }
}
