package com.dididi.booking.hotel.repository;

import com.dididi.booking.hotel.domain.entity.RoomType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoomTypeRepository extends JpaRepository<RoomType, Long> {
    List<RoomType> findByHotelIdOrderByBasePrice(Long hotelId);

    /**
     * Lay loai phong kem khoa ghi (SELECT ... FOR UPDATE) de cac don DIRECT cung loai phong
     * SERIAL HOA voi nhau truoc khi quet ton kho + insert (BP-BK-02) -> chong oversell.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rt from RoomType rt where rt.id = :id")
    Optional<RoomType> findByIdForUpdate(@Param("id") Long id);
}
