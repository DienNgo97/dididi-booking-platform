package com.dididi.booking.gateway.api.dto;

import com.dididi.booking.gateway.domain.PaymentGatewayConfig;

/** hashSecret duoc CHE (chi hien 4 ky tu cuoi) khi tra ve. */
public record PaymentGatewayConfigDto(
        String provider, String tmnCode, String hashSecretMasked,
        String payUrl, String returnUrl, boolean enabled) {

    public static PaymentGatewayConfigDto from(PaymentGatewayConfig c) {
        return new PaymentGatewayConfigDto(c.getProvider(), c.getTmnCode(), mask(c.getHashSecret()),
                c.getPayUrl(), c.getReturnUrl(), c.isEnabled());
    }

    private static String mask(String s) {
        if (s == null || s.isBlank()) return "";
        if (s.length() <= 4) return "****";
        return "****" + s.substring(s.length() - 4);
    }
}
