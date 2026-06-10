package com.dididi.booking.payment.repository;

import com.dididi.booking.payment.domain.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {
    Optional<Refund> findByBookingId(Long bookingId);
    List<Refund> findAllByOrderByCreatedAtDesc();
}
