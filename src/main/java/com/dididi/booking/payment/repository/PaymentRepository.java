package com.dididi.booking.payment.repository;

import com.dididi.booking.payment.domain.entity.Payment;
import com.dididi.booking.payment.domain.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByBookingId(Long bookingId);

    /**
     * P2: khoá dòng giao dịch khi đối soát. Chạy nhiều instance (hoặc đối soát trùng lúc IPN về)
     * thì hai luồng cùng thấy PENDING và cùng xử lý — hiếm nhưng hậu quả là ghi nhận trùng.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);
    Optional<Payment> findByTransactionRef(String transactionRef);

    /**
     * Giao dịch treo cần đối soát với cổng: đang PENDING, đi qua VNPay, tạo trong khoảng
     * [notBefore, notAfter]. Chặn dưới để khỏi hỏi mãi giao dịch quá cũ, chặn trên để khỏi
     * hỏi khi khách còn đang gõ OTP.
     */
    List<Payment> findByStatusAndMethodAndCreatedAtBetween(
            PaymentStatus status, String method, Instant notBefore, Instant notAfter);
}
