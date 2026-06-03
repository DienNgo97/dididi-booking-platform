package com.dididi.booking.flight.repository;

import com.dididi.booking.flight.domain.entity.Flight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FlightRepository extends JpaRepository<Flight, Long> {
    Optional<Flight> findByExternalId(Long externalId);
    List<Flight> findAllByOrderByDepartureTime();
}
