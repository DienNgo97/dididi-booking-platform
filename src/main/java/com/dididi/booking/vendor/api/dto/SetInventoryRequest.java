package com.dididi.booking.vendor.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Đặt số phòng trống cho MỌI ngày trong [from, to] (gồm cả 2 đầu). price tuỳ chọn. */
public record SetInventoryRequest(
        @NotNull LocalDate from,
        @NotNull LocalDate to,
        @PositiveOrZero int availableRooms,
        BigDecimal price) {
}
