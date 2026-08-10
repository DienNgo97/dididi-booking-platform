package com.dididi.booking.promo.api.dto;

import com.dididi.booking.promo.domain.PromoCampaign;

import java.math.BigDecimal;

/** Cấu hình 1 chương trình khuyến mãi cá nhân hoá (cho màn hình admin). */
public record PromoCampaignDto(
        Long id,
        String type,
        String typeName,
        boolean enabled,
        String title,
        String description,
        String discountType,
        BigDecimal discountValue,
        BigDecimal maxDiscount,
        BigDecimal minOrderAmount,
        int validDays,
        int thresholdDays,
        String minTier,
        long grantedTotal) {

    public static PromoCampaignDto from(PromoCampaign c, long grantedTotal) {
        return new PromoCampaignDto(
                c.getId(),
                c.getType().name(),
                c.getType().getViName(),
                c.isEnabled(),
                c.getTitle(),
                c.getDescription(),
                c.getDiscountType().name(),
                c.getDiscountValue(),
                c.getMaxDiscount(),
                c.getMinOrderAmount(),
                c.getValidDays(),
                c.getThresholdDays(),
                c.getMinTier(),
                grantedTotal);
    }
}
