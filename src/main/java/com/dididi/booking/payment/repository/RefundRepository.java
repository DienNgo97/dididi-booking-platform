package com.dididi.booking.payment.repository;

import com.dididi.booking.payment.domain.entity.Refund;
import com.dididi.booking.payment.domain.enums.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {
    Optional<Refund> findByBookingId(Long bookingId);
    List<Refund> findAllByOrderByCreatedAtDesc();

    /** P1-4: các khoản đã ghi sổ nhưng tiền chưa chuyển — danh sách việc của kế toán. */
    List<Refund> findByStatusOrderByIdDesc(RefundStatus status);
}
