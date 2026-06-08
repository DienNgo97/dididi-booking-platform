package com.dididi.booking.hotel.repository;

import com.dididi.booking.hotel.domain.entity.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomTypeRepository extends JpaRepository<RoomType, Long> {
    List<RoomType> findByHotelIdOrderByBasePrice(Long hotelId);
}
