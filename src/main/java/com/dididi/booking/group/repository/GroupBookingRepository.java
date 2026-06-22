package com.dididi.booking.group.repository;

import com.dididi.booking.group.domain.entity.GroupBooking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupBookingRepository extends JpaRepository<GroupBooking, Long> {
    Optional<GroupBooking> findByToken(String token);
    List<GroupBooking> findByOrganizerUserIdOrderByCreatedAtDesc(Long organizerUserId);
}
