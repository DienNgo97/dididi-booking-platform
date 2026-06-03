package com.dididi.booking.hotel.api.dto;

import jakarta.validation.constraints.NotBlank;

public record HotelUpsertRequest(
        @NotBlank String name,
        String city,
        String address,
        String description,
        Integer starRating,
        Boolean active) {
}
