package com.dididi.booking.hotel.repository;

import com.dididi.booking.hotel.domain.entity.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HotelRepository extends JpaRepository<Hotel, Long> {
    Optional<Hotel> findByExternalId(Long externalId);
    List<Hotel> findByActiveTrue();
    List<Hotel> findByActiveTrueAndCityContainingIgnoreCase(String city);

    // ---- Phase 7 (vendor / DIRECT) ----
    Optional<Hotel> findByVendorId(Long vendorId);
}
