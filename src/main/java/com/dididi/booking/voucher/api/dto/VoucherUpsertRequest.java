package com.dididi.booking.voucher.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record VoucherUpsertRequest(
        String code, String description, String discountType, BigDecimal discountValue,
        BigDecimal maxDiscount, BigDecimal minOrderAmount, Integer usageLimit, Integer perUserLimit,
        Instant validFrom, Instant validTo, Boolean active) {
}
