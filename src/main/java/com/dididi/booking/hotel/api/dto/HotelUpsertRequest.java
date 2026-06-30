package com.dididi.booking.hotel.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record HotelUpsertRequest(
        @NotBlank String name,
        String city,
        String address,
        // dia chi tach nho
        String houseNumber,
        String street,
        String ward,
        String district,
        String province,
        // toa do (vendor pin tren ban do)
        Double lat,
        Double lng,
        String description,
        Integer starRating,
        Boolean active,
        // phan loai / recommendation
        String propertyType,
        String region,
        List<String> amenities,
        List<String> tags) {
}
