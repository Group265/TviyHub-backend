package team.project.TviyHub.subscription;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.booking.BookingRepository;
import team.project.TviyHub.exception.ConflictException;
import team.project.TviyHub.exception.ResourceNotFoundException;
import team.project.TviyHub.payment.Payment;
import team.project.TviyHub.subscription.dto.SubscriptionRequest;
import team.project.TviyHub.user.User;
import team.project.TviyHub.user.UserRepository;
import team.project.TviyHub.workspace.Workspace;
import team.project.TviyHub.workspace.WorkspaceRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final WorkspaceRepository workspaceRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final SubscriptionMapper subscriptionMapper;

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Subscription createSubscription(Long userId, SubscriptionRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Workspace workspace = workspaceRepository.findByIdForUpdate(request.workspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id: " + request.workspaceId()));

        if (workspace.getType() != Workspace.WorkspaceType.DESK) {
            throw new ConflictException("Subscriptions can only be created for DESK resources. MEETING_ROOM is hourly only.");
        }

        LocalDate startDate = request.startDate();
        LocalDate endDate = startDate.plusMonths(1).minusDays(1);

        List<Subscription> overlaps = subscriptionRepository.findOverlappingSubscriptionsInPeriod(
                workspace.getId(),
                startDate,
                endDate
                );
        if (!overlaps.isEmpty()) {
            throw new ConflictException("The desk is already held or active for the selected dates");
        }

        boolean hasTargetBookings = bookingRepository.existsActiveBookingsInPeriod(workspace.getId(), startDate, endDate);
        if (hasTargetBookings) {
            throw new ConflictException("Cannot create subscription: this desk already has standard hourly/daily bookings within this period.");
        }

        Payment pendingPayment = Payment.builder()
                .status(Payment.PaymentStatus.UNPAID)
                .amount(BigDecimal.valueOf(3500.00))
                .build();

        Subscription subscription = subscriptionMapper.toEntity(request);

        subscription.setUser(user);
        subscription.setWorkspace(workspace);
        subscription.setEndDate(endDate);
        subscription.setStatus(Subscription.SubscriptionStatus.PENDING_PAYMENT);
        subscription.setPayment(pendingPayment);

        return subscriptionRepository.save(subscription);
    }

    @Transactional(readOnly = true)
    public List<Subscription> getAllSubscriptions() {
        return subscriptionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Subscription> getUserSubscriptions(Long userId) {
        return subscriptionRepository.getUserSubscriptions(userId);
    }

    @Transactional
    public Subscription confirmPayment(Long id) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found with id: " + id));

        if (subscription.getStatus() != Subscription.SubscriptionStatus.PENDING_PAYMENT) {
            throw new ConflictException("Subscription cannot be paid from its current state: " + subscription.getStatus());
        }

        subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        subscription.getPayment().setStatus(Payment.PaymentStatus.PAID);
        subscription.getPayment().setPaidAt(LocalDateTime.now());

        return subscriptionRepository.save(subscription);
    }

    @Transactional
    public Subscription cancelSubscription(Long id) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found with id: " + id));

        subscription.setStatus(Subscription.SubscriptionStatus.CANCELLED);
        if (subscription.getPayment().getStatus() == Payment.PaymentStatus.PAID) {
            subscription.getPayment().setStatus(Payment.PaymentStatus.REFUNDED);
        }
        return subscriptionRepository.save(subscription);
    }

    @Transactional
    public Subscription extendSubscription(Long id, int days) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found with id: " + id));

        if (subscription.getStatus() != Subscription.SubscriptionStatus.ACTIVE) {
            throw new ConflictException("Only ACTIVE subscriptions can be extended");
        }

        if (days <= 0) {
            throw new ConflictException("Extension days must be greater than zero");
        }

        LocalDate checkStartDate = subscription.getEndDate().plusDays(1);
        LocalDate newEndDate = subscription.getEndDate().plusDays(days);

        List<Subscription> overlaps = subscriptionRepository
                .findOverlappingSubscriptionsInPeriod(
                        subscription.getWorkspace().getId(),
                        checkStartDate,
                        newEndDate
                );
        if (!overlaps.isEmpty()) {
            throw new ConflictException("Cannot extend subscription: the extension period conflicts with another client's upcoming subscription");
        }

        boolean hasTargetBookings = bookingRepository.existsActiveBookingsInPeriod(
                subscription.getWorkspace().getId(),
                checkStartDate,
                newEndDate
        );
        if (hasTargetBookings) {
            throw new ConflictException("Cannot extend subscription: this desk already has standard hourly/daily bookings within the extension period.");
        }
        subscription.setEndDate(newEndDate);
        return subscriptionRepository.save(subscription);
    }
}
