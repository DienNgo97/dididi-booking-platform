package com.dididi.booking.common.dto;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        boolean success,
        String code,
        String message,
        Map<String, String> fieldErrors,
        Instant timestamp) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(false, code, message, null, Instant.now());
    }

    public static ErrorResponse of(String code, String message, Map<String, String> fieldErrors) {
        return new ErrorResponse(false, code, message, fieldErrors, Instant.now());
    }
}
