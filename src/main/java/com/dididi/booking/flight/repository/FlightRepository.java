package com.dididi.booking.flight.repository;

import com.dididi.booking.flight.domain.entity.Flight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FlightRepository extends JpaRepository<Flight, Long> {

    /** Tìm kiếm admin theo số hiệu / sân bay đi / đến (thanh tìm kiếm tab Chuyến bay). */
    @org.springframework.data.jpa.repository.Query("""
            SELECT f FROM Flight f
            WHERE lower(f.flightNumber) LIKE lower(concat('%', :q, '%'))
               OR lower(f.fromAirport) LIKE lower(concat('%', :q, '%'))
               OR lower(f.toAirport) LIKE lower(concat('%', :q, '%'))
            """)
    org.springframework.data.domain.Page<Flight> adminSearch(
            @org.springframework.data.repository.query.Param("q") String q,
            org.springframework.data.domain.Pageable pageable);
    Optional<Flight> findByExternalId(Long externalId);
    List<Flight> findAllByOrderByDepartureTime();

    /**
     * CHỈ chuyến ĐỒNG BỘ TỪ FLIGHT-PROVIDER (externalId &lt; base, tức &lt; 900000).
     * Chỉ những chuyến này có sơ đồ ghế (getSeatMap) nên khách chọn được chỗ ngồi.
     * Chuyến demo CỤC BỘ (externalId &gt;= 900000: DemoDataSeeder + seeder ×5) KHÔNG có sơ đồ ghế
     * -> loại khỏi mọi danh sách/tìm kiếm cho khách để không lọt vé "không chọn được ghế".
     * externalId = null cũng bị loại (điều kiện &lt; không khớp null) — an toàn.
     * Vẫn tra bằng findById được, nên đơn cũ tham chiếu chuyến cục bộ không gãy.
     */
    List<Flight> findByExternalIdLessThanOrderByDepartureTime(Long externalId);

    /**
     * Tru ghe nguyen tu cho ve cuc bo (BP-BK-01): chi tru khi con du ghe.
     * Tra ve so dong da cap nhat (1 = tru thanh cong, 0 = khong du ghe / het ve) -> chong oversell.
     */
    @Modifying
    @Query("update Flight f set f.availableSeats = f.availableSeats - :n where f.id = :id and f.availableSeats >= :n")
    int decrementSeatsIfAvailable(@Param("id") Long id, @Param("n") int n);

    /**
     * Cong ghe lai cho ve cuc bo (BP-BK-09) khi HUY / HET HAN / HOAN TIEN: dao nguoc decrementSeatsIfAvailable.
     * Bat buoc co (truoc day thieu) — neu khong, ghe vé local bi tru roi khong bao gio hoan -> ro ri vinh vien.
     */
    @Modifying
    @Query("update Flight f set f.availableSeats = f.availableSeats + :n where f.id = :id")
    int incrementSeats(@Param("id") Long id, @Param("n") int n);
}
