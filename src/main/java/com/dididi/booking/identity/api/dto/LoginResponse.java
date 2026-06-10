package com.dididi.booking.identity.api.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInMinutes,
        String email,
        String role) {
}
