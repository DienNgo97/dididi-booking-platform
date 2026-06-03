package com.dididi.booking.hotel.api.dto;

import com.dididi.booking.hotel.domain.entity.Hotel;

import java.math.BigDecimal;

public record HotelApiDto(
        Long id,
        String name,
        String city,
        String address,
        String description,
        Integer starRating,
        BigDecimal minPrice,
        String currency) {

    public static HotelApiDto from(Hotel h) {
        return new HotelApiDto(h.getId(), h.getName(), h.getCity(), h.getAddress(),
                h.getDescription(), h.getStarRating(), h.getMinPrice(), h.getCurrency());
    }
}
