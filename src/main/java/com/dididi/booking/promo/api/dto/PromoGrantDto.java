package com.dididi.booking.promo.api.dto;

import com.dididi.booking.promo.domain.PromoGrant;

import java.time.Instant;

/** 1 lần tặng voucher cá nhân hoá (lịch sử cho admin). */
public record PromoGrantDto(
        Long id,
        String type,
        String typeName,
        Long userId,
        String userEmail,
        String userName,
        String voucherCode,
        String cycleKey,
        String note,
        Instant grantedAt) {

    public static PromoGrantDto from(PromoGrant g, String email, String name) {
        return new PromoGrantDto(g.getId(), g.getType().name(), g.getType().getViName(),
                g.getUserId(), email, name, g.getVoucherCode(), g.getCycleKey(), g.getNote(), g.getCreatedAt());
    }
}
