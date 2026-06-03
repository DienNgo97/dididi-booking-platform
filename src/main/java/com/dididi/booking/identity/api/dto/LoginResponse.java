package com.dididi.booking.identity.api.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInMinutes,
        String email,
        String role) {
}
