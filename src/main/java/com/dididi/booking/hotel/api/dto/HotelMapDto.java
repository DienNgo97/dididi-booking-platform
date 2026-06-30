package com.dididi.booking.hotel.api.dto;

import com.dididi.booking.hotel.domain.entity.Hotel;

import java.io.Serializable;
import java.math.BigDecimal;

/** DTO gọn cho marker trên bản đồ (chỉ field cần để vẽ pin + popup). */
public record HotelMapDto(
        Long id,
        String name,
        double lat,
        double lng,
        Integer starRating,
        BigDecimal minPrice,
        String currency,
        String city,
        String propertyType) implements Serializable {

    private static final long serialVersionUID = 1L;

    public static HotelMapDto from(Hotel h) {
        return new HotelMapDto(
                h.getId(), h.getName(), h.getLat(), h.getLng(),
                h.getStarRating(), h.getMinPrice(), h.getCurrency(), h.getCity(),
                h.getPropertyType() != null ? h.getPropertyType().name() : null);
    }
}
