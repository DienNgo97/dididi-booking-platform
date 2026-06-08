package com.dididi.booking.booking.repository;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByPublicCode(String publicCode);
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

    // ---- Admin (Phase 4b) ----
    Page<Booking> findByStatus(BookingStatus status, Pageable pageable);
    long countByStatus(BookingStatus status);
    List<Booking> findTop5ByOrderByCreatedAtDesc();
}
