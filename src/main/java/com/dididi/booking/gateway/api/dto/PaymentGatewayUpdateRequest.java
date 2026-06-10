package com.dididi.booking.gateway.api.dto;

/** hashSecret de trong -> giu nguyen secret cu (khong ghi de bang gia tri che). */
public record PaymentGatewayUpdateRequest(
        String tmnCode, String hashSecret, String payUrl, String returnUrl, Boolean enabled) {
}
