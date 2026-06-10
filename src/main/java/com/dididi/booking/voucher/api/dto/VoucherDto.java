package com.dididi.booking.voucher.api.dto;

import com.dididi.booking.voucher.domain.Voucher;

import java.math.BigDecimal;
import java.time.Instant;

public record VoucherDto(
        Long id, String code, String description, String discountType, BigDecimal discountValue,
        BigDecimal maxDiscount, BigDecimal minOrderAmount, Integer usageLimit, Integer perUserLimit,
        Instant validFrom, Instant validTo, boolean active) {

    public static VoucherDto from(Voucher v) {
        return new VoucherDto(v.getId(), v.getCode(), v.getDescription(), v.getDiscountType().name(),
                v.getDiscountValue(), v.getMaxDiscount(), v.getMinOrderAmount(), v.getUsageLimit(),
                v.getPerUserLimit(), v.getValidFrom(), v.getValidTo(), v.isActive());
    }
}
