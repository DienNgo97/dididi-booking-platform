package com.dididi.booking.vendor.api.dto;

import com.dididi.booking.hotel.domain.entity.RoomType;

import java.math.BigDecimal;

public record RoomTypeDto(
        Long id,
        Long hotelId,
        String name,
        String description,
        int capacity,
        BigDecimal basePrice,
        String currency,
        int totalRooms) {

    public static RoomTypeDto from(RoomType r) {
        return new RoomTypeDto(r.getId(), r.getHotelId(), r.getName(), r.getDescription(),
                r.getCapacity(), r.getBasePrice(), r.getCurrency(), r.getTotalRooms());
    }
}
