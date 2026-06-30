package com.dididi.booking.hotel.repository;

import com.dididi.booking.hotel.domain.entity.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HotelRepository extends JpaRepository<Hotel, Long> {
    Optional<Hotel> findByExternalId(Long externalId);
    List<Hotel> findByActiveTrue();
    List<Hotel> findByActiveTrueAndCityContainingIgnoreCase(String city);

    /** Tim khach san dang hoat dong theo tu khoa: khop ten / thanh pho / dia chi (khong phan biet hoa thuong). */
    @Query("select h from Hotel h where h.active = true and (" +
           "lower(h.name) like lower(concat('%', :kw, '%')) or " +
           "lower(h.city) like lower(concat('%', :kw, '%')) or " +
           "lower(h.address) like lower(concat('%', :kw, '%')))")
    List<Hotel> searchActiveByKeyword(@Param("kw") String kw);

    // ---- Phase 7 (vendor / DIRECT) ----
    Optional<Hotel> findByVendorId(Long vendorId);

    // ---- Google Maps / geo (Nhom 2) ----
    /** Khach san co toa do trong khung nhin ban do (bounding box). */
    List<Hotel> findByActiveTrueAndLatBetweenAndLngBetween(double minLat, double maxLat, double minLng, double maxLng);

    /** Tat ca khach san dang hoat dong da co toa do (de ve marker). */
    List<Hotel> findByActiveTrueAndLatIsNotNullAndLngIsNotNull();
}
