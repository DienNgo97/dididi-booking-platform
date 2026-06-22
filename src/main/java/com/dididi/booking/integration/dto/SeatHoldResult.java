package com.dididi.booking.integration.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Ket qua giu cho / xac nhan tu flight-provider. */
public record SeatHoldResult(
        String holdRef,
        Instant expiresAt,
        List<SeatItem> seats,
        BigDecimal totalPrice,
        String currency,
        long remainingSeconds) {
}
