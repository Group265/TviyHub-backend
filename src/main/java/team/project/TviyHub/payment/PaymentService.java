package team.project.TviyHub.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.exception.ResourceNotFoundException;
import team.project.TviyHub.payment.dto.PaymentUpdateDto;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

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

        payment.setStatus(dto.status());
        payment.setPaymentMethod(dto.paymentMethod());

        if (dto.status() == Payment.PaymentStatus.PAID) {
            payment.setPaidAt(LocalDateTime.now());
        } else if (dto.status() == Payment.PaymentStatus.UNPAID) {
            payment.setPaidAt(null);
        }

        return paymentRepository.save(payment);
    }
}
