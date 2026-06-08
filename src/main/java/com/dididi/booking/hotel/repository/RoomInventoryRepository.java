package com.dididi.booking.hotel.repository;

import com.dididi.booking.hotel.domain.entity.RoomInventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoomInventoryRepository extends JpaRepository<RoomInventory, Long> {
    List<RoomInventory> findByRoomTypeIdAndDateBetweenOrderByDate(Long roomTypeId, LocalDate from, LocalDate to);
    Optional<RoomInventory> findByRoomTypeIdAndDate(Long roomTypeId, LocalDate date);
    List<RoomInventory> findByRoomTypeId(Long roomTypeId);
}
