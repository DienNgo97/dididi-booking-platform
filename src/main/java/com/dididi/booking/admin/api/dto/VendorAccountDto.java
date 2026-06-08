package com.dididi.booking.admin.api.dto;

public record VendorAccountDto(
        Long userId,
        String email,
        String fullName,
        String status,        // ACTIVE / INACTIVE (cho duyet) / LOCKED (tu choi)
        Long hotelId,
        String hotelName,
        boolean hotelActive) {
}
