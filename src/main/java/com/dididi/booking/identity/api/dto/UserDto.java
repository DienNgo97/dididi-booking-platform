package com.dididi.booking.identity.api.dto;

public record UserDto(
        Long id,
        String email,
        String fullName,
        String role) {
}
