package com.dididi.booking.payment.repository;

import com.dididi.booking.payment.domain.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
    List<PaymentAttempt> findByBookingIdOrderByIdDesc(Long bookingId);
    List<PaymentAttempt> findByTxnRefOrderByIdDesc(String txnRef);
}
