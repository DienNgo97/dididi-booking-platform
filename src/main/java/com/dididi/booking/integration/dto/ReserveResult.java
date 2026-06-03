package com.dididi.booking.integration.dto;

import java.math.BigDecimal;

/** Khop ReservationResponse tu hotel-pms. */
public record ReserveResult(
        Long reservationId,
        String confirmationCode,
        Long hotelId,
        Long roomTypeId,
        java.time.LocalDate checkIn,
        java.time.LocalDate checkOut,
        Integer rooms,
        BigDecimal totalPrice,
        String currency,
        String status) {
}
