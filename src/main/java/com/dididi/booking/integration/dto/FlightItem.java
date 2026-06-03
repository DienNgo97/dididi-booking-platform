package com.dididi.booking.integration.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Khop FlightDto tu flight-provider (Jackson bo qua field thua). */
public record FlightItem(
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
        String aircraftType) {
}
