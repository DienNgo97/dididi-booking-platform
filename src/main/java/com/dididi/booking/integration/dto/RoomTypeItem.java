package com.dididi.booking.integration.dto;

import java.math.BigDecimal;

/** Khop RoomTypeDto tu hotel-pms. */
public record RoomTypeItem(
        Long id,
        Long hotelId,
        String name,
        String description,
        Integer capacity,
        BigDecimal basePrice,
        String currency,
        Integer totalRooms) {
}
