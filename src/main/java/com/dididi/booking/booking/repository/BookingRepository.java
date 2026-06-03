package com.dididi.booking.booking.repository;

import com.dididi.booking.booking.domain.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByPublicCode(String publicCode);
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);
}
