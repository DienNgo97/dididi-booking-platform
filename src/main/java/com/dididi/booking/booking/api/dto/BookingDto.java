package com.dididi.booking.booking.api.dto;

import com.dididi.booking.booking.domain.entity.Booking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BookingDto(
        String publicCode,
        String type,
        String title,
        String status,
        String providerConfirmation,
        LocalDate checkIn,
        LocalDate checkOut,
        LocalDateTime travelDate,
        int quantity,
        BigDecimal amount,
        String currency,
        Long groupId) {

    public static BookingDto from(Booking b) {
        return new BookingDto(b.getPublicCode(), b.getType().name(), b.getTitle(),
                b.getStatus().name(), b.getProviderConfirmation(), b.getCheckIn(), b.getCheckOut(),
                b.getTravelDate(), b.getQuantity(), b.getAmount(), b.getCurrency(), b.getGroupId());
    }
}
