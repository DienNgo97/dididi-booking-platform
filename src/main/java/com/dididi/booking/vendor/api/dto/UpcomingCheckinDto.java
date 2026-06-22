package com.dididi.booking.vendor.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 1 đơn sắp nhận phòng (cho dashboard). */
public record UpcomingCheckinDto(
        String publicCode,
        String title,
        LocalDate checkIn,
        int rooms,
        BigDecimal amount,
        String roomTypeName) {
}
