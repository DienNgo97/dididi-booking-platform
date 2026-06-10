package com.dididi.booking.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Body cho POST /api/auth/refresh va /api/auth/logout. */
public record RefreshRequest(@NotBlank String refreshToken) {
}
