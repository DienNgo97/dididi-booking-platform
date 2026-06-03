package com.dididi.booking.booking.api.dto;

import java.time.LocalDate;

/** type = "FLIGHT" hoac "HOTEL". Dien field tuong ung. */
public record CreateBookingRequest(
        String type,
        // flight
        Long flightId,
        Integer seats,
        String passengerName,
        String contactEmail,
        // hotel
        Long hotelId,
        Long roomTypeId,
        String roomName,
        String guestName,
        LocalDate checkIn,
        LocalDate checkOut,
        Integer rooms) {
}
