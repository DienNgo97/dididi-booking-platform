package com.dididi.booking.flight.repository;

import com.dididi.booking.flight.domain.entity.Flight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FlightRepository extends JpaRepository<Flight, Long> {
    Optional<Flight> findByExternalId(Long externalId);
    List<Flight> findAllByOrderByDepartureTime();

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
