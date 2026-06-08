package com.dididi.booking.vendor.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record RoomTypeUpsertRequest(
        @NotBlank String name,
        String description,
        @Positive int capacity,
        @NotNull @PositiveOrZero BigDecimal basePrice,
        String currency,
        @PositiveOrZero int totalRooms) {
}
