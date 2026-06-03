package com.dididi.booking.integration.dto;

/** Khop HotelDto tu hotel-pms. */
public record HotelItem(
        Long id,
        String name,
        String city,
        String address,
        String description,
        Integer starRating) {
}
