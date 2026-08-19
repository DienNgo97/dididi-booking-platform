package com.dididi.booking.payment.repository;

import com.dididi.booking.payment.domain.entity.Payment;
import com.dididi.booking.payment.domain.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByBookingId(Long bookingId);
    Optional<Payment> findByTransactionRef(String transactionRef);

    /**
     * Giao dịch treo cần đối soát với cổng: đang PENDING, đi qua VNPay, tạo trong khoảng
     * [notBefore, notAfter]. Chặn dưới để khỏi hỏi mãi giao dịch quá cũ, chặn trên để khỏi
     * hỏi khi khách còn đang gõ OTP.
     */
    List<Payment> findByStatusAndMethodAndCreatedAtBetween(
            PaymentStatus status, String method, Instant notBefore, Instant notAfter);
}
