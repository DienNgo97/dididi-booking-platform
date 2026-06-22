package com.dididi.booking.admin.api.dto;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.domain.enums.BookingType;
import com.dididi.booking.booking.domain.enums.CancelStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** View đơn cho màn hình admin (Phase 4b). */
public record AdminBookingDto(
        Long id,
        String publicCode,
        Long userId,
        BookingType type,
        String title,
        BookingStatus status,
        BigDecimal amount,
        String currency,
        int quantity,
        LocalDate checkIn,
        LocalDate checkOut,
        LocalDateTime travelDate,
        String providerConfirmation,
        Instant createdAt,
        CancelStatus cancelStatus,
        String cancelReason,
        String cancelAdminNote) {

    public static AdminBookingDto from(Booking b) {
        return new AdminBookingDto(
                b.getId(), b.getPublicCode(), b.getUserId(), b.getType(), b.getTitle(),
                b.getStatus(), b.getAmount(), b.getCurrency(), b.getQuantity(),
                b.getCheckIn(), b.getCheckOut(), b.getTravelDate(),
                b.getProviderConfirmation(), b.getCreatedAt(),
                b.getCancelStatus(), b.getCancelReason(), b.getCancelAdminNote());
    }
}
