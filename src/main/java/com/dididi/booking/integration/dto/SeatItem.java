package com.dididi.booking.integration.dto;

import java.math.BigDecimal;

/** 1 ghe trong so do tu flight-provider (deserialize JSON theo ten field). */
public record SeatItem(
        String code,
        int row,
        String col,
        String seatClass,   // BUSINESS / ECONOMY
        String position,    // WINDOW / AISLE / MIDDLE
        BigDecimal price,
        String status) {    // FREE / HELD / BOOKED
}
