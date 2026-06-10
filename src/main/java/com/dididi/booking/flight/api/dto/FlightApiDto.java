package com.dididi.booking.flight.api.dto;

import com.dididi.booking.flight.domain.entity.Flight;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FlightApiDto(
        Long id,
        String flightNumber,
        String airlineCode,
        String from,
        String to,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        BigDecimal price,
        String currency,
        Integer availableSeats,
        String aircraftType) implements Serializable {

    private static final long serialVersionUID = 1L;

    public static FlightApiDto from(Flight f) {
        return new FlightApiDto(f.getId(), f.getFlightNumber(), f.getAirlineCode(),
                f.getFromAirport(), f.getToAirport(), f.getDepartureTime(), f.getArrivalTime(),
                f.getPrice(), f.getCurrency(), f.getAvailableSeats(), f.getAircraftType());
    }
}
