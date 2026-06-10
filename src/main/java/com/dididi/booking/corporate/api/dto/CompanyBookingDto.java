package com.dididi.booking.corporate.api.dto;

import com.dididi.booking.booking.domain.entity.Booking;

import java.math.BigDecimal;
import java.time.Instant;

public record CompanyBookingDto(
        String publicCode, String type, String title, BigDecimal amount, String currency,
        String status, Long userId, Instant createdAt) {

    public static CompanyBookingDto from(Booking b) {
        return new CompanyBookingDto(b.getPublicCode(),
                b.getType() == null ? null : b.getType().name(), b.getTitle(),
                b.getAmount(), b.getCurrency(),
                b.getStatus() == null ? null : b.getStatus().name(),
                b.getUserId(), b.getCreatedAt());
    }
}
