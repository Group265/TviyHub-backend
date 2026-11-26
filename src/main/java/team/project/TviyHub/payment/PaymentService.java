package team.project.TviyHub.payment;

import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.booking.Booking;
import team.project.TviyHub.exception.ConflictException;
import team.project.TviyHub.exception.ResourceNotFoundException;
import team.project.TviyHub.payment.dto.PaymentUpdateDto;
import team.project.TviyHub.payment.dto.StripeSessionResponseDto;
import team.project.TviyHub.subscription.Subscription;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
    private static final ZoneId KYIV_ZONE = ZoneId.of("Europe/Kyiv");

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

        if (dto.status() == Payment.PaymentStatus.PAID) {
            validatePayableState(payment);
        }

        return updatePaymentFields(payment, dto.status(), dto.paymentMethod());
    }

    @Transactional
    public StripeSessionResponseDto createStripeSession(
            Long paymentId,
            String successUrl,
            String cancelUrl,
            Long currentUserId
    ) {
        Payment payment = getPaymentById(paymentId);

        validatePaymentOwnership(payment, currentUserId);
        validatePayableState(payment);

        long stripeAmount = payment.getAmount().multiply(new BigDecimal(100)).longValue();

        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(cancelUrl)
                    .setExpiresAt(calculateStripeExpiresAt(payment))
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
                                                                    .setName(buildProductName(payment))
                                                                    .setDescription(buildProductDescription(payment))
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
    public StripeSessionResponseDto createBatchStripeSession(
            List<Long> paymentIds,
            String successUrl,
            String cancelUrl,
            Long currentUserId
    ) {
        List<Long> uniqueIds = paymentIds.stream().distinct().toList();
        List<Payment> payments = paymentRepository.findAllById(uniqueIds);

        if (payments.size() != uniqueIds.size()) {
            throw new ResourceNotFoundException("One or more payment transactions were not found.");
        }

        for (Payment payment : payments) {
            validatePaymentOwnership(payment, currentUserId);
            validatePayableState(payment);
        }

        long earliestExpiresAt = payments.stream()
                .mapToLong(this::calculateStripeExpiresAt)
                .min()
                .orElseGet(() -> ZonedDateTime.now(KYIV_ZONE).plusHours(24).toEpochSecond());

        SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(cancelUrl)
                .setExpiresAt(earliestExpiresAt)
                .setCurrency("uah");

        for (Payment payment : payments) {
            long stripeAmount = payment.getAmount().multiply(new BigDecimal(100)).longValue();

            paramsBuilder.addLineItem(
                    SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(
                                    SessionCreateParams.LineItem.PriceData.builder()
                                            .setCurrency("uah")
                                            .setUnitAmount(stripeAmount)
                                            .setProductData(
                                                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                            .setName(buildProductName(payment))
                                                            .setDescription(buildProductDescription(payment))
                                                            .build()
                                            )
                                            .build()
                            )
                            .build()
            );
        }

        try {
            Session session = stripeClient.checkout().sessions().create(paramsBuilder.build());
            for (Payment payment : payments) {
                payment.setExternalTransactionId(session.getId());
                payment.setPaymentMethod(Payment.PaymentMethod.STRIPE);
            }
            paymentRepository.saveAll(payments);

            return new StripeSessionResponseDto(session.getUrl(), session.getId());
        } catch (StripeException e) {
            throw new RuntimeException("Unable to create batch Stripe payment session", e);
        }
    }

    private String buildProductName(Payment payment) {
        if (payment.getBooking() != null && payment.getBooking().getWorkspace() != null) {
            return payment.getBooking().getWorkspace().getName();
        } else if (payment.getSubscription() != null && payment.getSubscription().getWorkspace() != null) {
            return payment.getSubscription().getWorkspace().getName();
        }
        return "Оплата послуг хабу #" + payment.getId();
    }

    private String buildProductDescription(Payment payment) {
        if (payment.getBooking() != null) {
            Booking b = payment.getBooking();
            String timeRange = (b.getStartTime() != null && b.getEndTime() != null)
                    ? ", " + b.getStartTime() + "–" + b.getEndTime()
                    : "";
            return "Бронювання • " + b.getBookingDate() + timeRange;
        } else if (payment.getSubscription() != null) {
            Subscription s = payment.getSubscription();
            return "Абонемент • з " + s.getStartDate() + " до " + s.getEndDate();
        }
        return null;
    }

    @Transactional
    public List<Payment> verifyStripePayment(String sessionId) {
        List<Payment> payments = paymentRepository.findAllByExternalTransactionId(sessionId);
        if (payments.isEmpty()) {
            throw new ResourceNotFoundException("Payments with Stripe session ID not found: " + sessionId);
        }

        boolean allPaid = payments.stream().allMatch(p -> p.getStatus() == Payment.PaymentStatus.PAID);
        if (allPaid) {
            return payments;
        }

        try {
            Session session = stripeClient.checkout().sessions().retrieve(sessionId);

            if ("paid".equals(session.getPaymentStatus())) {
                return payments.stream()
                        .map(p -> p.getStatus() == Payment.PaymentStatus.PAID
                                ? p
                                : updatePaymentFields(p, Payment.PaymentStatus.PAID, Payment.PaymentMethod.STRIPE))
                        .toList();
            }
            throw new IllegalStateException("Stripe payment was not completed successfully. Status: " + session.getPaymentStatus());
        } catch (StripeException e) {
            throw new RuntimeException("Error verifying Stripe session status", e);
        }
    }

    private void validatePaymentOwnership(Payment payment, Long currentUserId) {
        Long ownerId = null;
        if (payment.getBooking() != null && payment.getBooking().getUser() != null) {
            ownerId = payment.getBooking().getUser().getId();
        } else if (payment.getSubscription() != null && payment.getSubscription().getUser() != null) {
            ownerId = payment.getSubscription().getUser().getId();
        }

        if (ownerId == null || !ownerId.equals(currentUserId)) {
            throw new AccessDeniedException("You do not have permission to pay for this order.");
        }
    }

    private void validatePayableState(Payment payment) {
        if (payment.getStatus() == Payment.PaymentStatus.PAID) {
            throw new ConflictException("Payment is already paid.");
        }
        if (payment.getBooking() != null
                && payment.getBooking().getStatus() == Booking.BookingStatus.CANCELLED) {
            throw new ConflictException("Cannot pay for a cancelled booking.");
        }
        if (payment.getSubscription() != null
                && payment.getSubscription().getStatus() == Subscription.SubscriptionStatus.CANCELLED) {
            throw new ConflictException("Cannot pay for a cancelled subscription.");
        }
    }

    private long calculateStripeExpiresAt(Payment payment) {
        ZonedDateTime now = ZonedDateTime.now(KYIV_ZONE);
        long minExpiresAt = now.plusMinutes(31).toEpochSecond();
        long maxExpiresAt = now.plusHours(24).toEpochSecond();

        if (payment.getBooking() != null) {
            Booking booking = payment.getBooking();
            LocalTime start = booking.getStartTime() != null ? booking.getStartTime() : LocalTime.of(9, 0);
            ZonedDateTime bookingStart = LocalDateTime.of(booking.getBookingDate(), start).atZone(KYIV_ZONE);
            ZonedDateTime createdPlus24h = booking.getCreatedAt().plusHours(24).atZone(KYIV_ZONE);

            ZonedDateTime deadline = createdPlus24h.isBefore(bookingStart) ? createdPlus24h : bookingStart;
            long deadlineEpoch = deadline.toEpochSecond();

            return Math.max(minExpiresAt, Math.min(deadlineEpoch, maxExpiresAt));
        }

        if (payment.getSubscription() != null) {
            Subscription sub = payment.getSubscription();
            ZonedDateTime createdPlus24h = sub.getCreatedAt().plusHours(24).atZone(KYIV_ZONE);
            ZonedDateTime startDayClose = sub.getStartDate().atTime(19, 0).atZone(KYIV_ZONE);

            ZonedDateTime deadline = createdPlus24h.isBefore(startDayClose) ? createdPlus24h : startDayClose;
            long deadlineEpoch = deadline.toEpochSecond();
            return Math.max(minExpiresAt, Math.min(deadlineEpoch, maxExpiresAt));
        }

        return maxExpiresAt;
    }

    @Transactional
    public Payment confirmRefund(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));

        if (payment.getStatus() != Payment.PaymentStatus.REFUND_PENDING) {
            throw new ConflictException("Only payments with REFUND_PENDING status can be confirmed as refunded");
        }

        payment.setStatus(Payment.PaymentStatus.REFUNDED);
        payment.setRefundedAt(LocalDateTime.now());
        return paymentRepository.save(payment);
    }

    private Payment updatePaymentFields(Payment payment, Payment.PaymentStatus status, Payment.PaymentMethod method) {
        payment.setStatus(status);
        payment.setPaymentMethod(method);

        if (status == Payment.PaymentStatus.PAID) {
            payment.setPaidAt(LocalDateTime.now(KYIV_ZONE));

            if (payment.getSubscription() != null) {
                if (payment.getSubscription().getStatus() == Subscription.SubscriptionStatus.CANCELLED) {
                    log.error("CRITICAL: Payment {} succeeded for already CANCELLED subscription {}. Manual refund required!",
                            payment.getId(), payment.getSubscription().getId());
                } else {
                    payment.getSubscription().setStatus(Subscription.SubscriptionStatus.ACTIVE);
                }
            }

            if (payment.getBooking() != null) {
                if (payment.getBooking().getStatus() == Booking.BookingStatus.CANCELLED) {
                    log.error("CRITICAL: Payment {} succeeded for already CANCELLED booking {}. Manual refund required!",
                            payment.getId(), payment.getBooking().getId());
                } else {
                    payment.getBooking().setStatus(Booking.BookingStatus.CONFIRMED);
                }
            }
        } else if (status == Payment.PaymentStatus.UNPAID) {
            payment.setPaidAt(null);
        }
        return paymentRepository.save(payment);
    }

    @Value("${stripe.webhook.secret}")
    private String stripeWebhookSecret;

    @Transactional
    public void handleStripeWebhook(String payload, String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, stripeWebhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe webhook signature: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid Stripe signature");
        }

        if ("checkout.session.completed".equals(event.getType())) {
            EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
            if (dataObjectDeserializer.getObject().isPresent()
                    && dataObjectDeserializer.getObject().get() instanceof Session session) {

                if ("paid".equals(session.getPaymentStatus())) {
                    List<Payment> payments = paymentRepository.findAllByExternalTransactionId(session.getId());
                    if (payments.isEmpty()) {
                        log.warn("Payments not found for Stripe session ID: {}", session.getId());
                    } else  {
                        for (Payment payment : payments) {
                            if (payment.getStatus() != Payment.PaymentStatus.PAID) {
                                updatePaymentFields(payment, Payment.PaymentStatus.PAID, Payment.PaymentMethod.STRIPE);
                                log.info("Payment {} confirmed via Stripe webhook (session {})",
                                        payment.getId(), session.getId());
                            }
                        }
                    }
                }
            }
        }
    }
}
