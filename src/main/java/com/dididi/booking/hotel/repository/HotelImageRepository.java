package com.dididi.booking.hotel.repository;

import com.dididi.booking.hotel.domain.entity.HotelImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HotelImageRepository extends JpaRepository<HotelImage, Long> {
    List<HotelImage> findByHotelIdOrderBySortOrderAscIdAsc(Long hotelId);
    Optional<HotelImage> findFirstByHotelIdOrderBySortOrderAscIdAsc(Long hotelId);
    long countByHotelId(Long hotelId);
}
