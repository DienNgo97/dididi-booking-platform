package com.dididi.booking.loyalty.api.dto;

import com.dididi.booking.loyalty.domain.LoyaltyTransaction;

import java.time.Instant;

public record LoyaltyTxnDto(Long id, String type, int points, Long bookingId, String description, Instant createdAt) {
    public static LoyaltyTxnDto from(LoyaltyTransaction t) {
        return new LoyaltyTxnDto(t.getId(), t.getType().name(), t.getPoints(), t.getBookingId(),
                t.getDescription(), t.getCreatedAt());
    }
}
