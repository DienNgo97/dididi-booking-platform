package com.dididi.booking.integration.dto;

import java.math.BigDecimal;

/** Khop BookingResponse tu flight-provider. */
public record FlightBookResult(
        String confirmationCode,
        Long flightId,
        String flightNumber,
        Integer seats,
        BigDecimal totalPrice,
        String currency,
        String status) {
}
