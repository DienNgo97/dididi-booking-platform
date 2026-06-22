package com.dididi.booking.loyalty.api.dto;

import java.time.Instant;

/** 1 dong trong tab "Voucher da doi" cua khach. */
public record RedeemedVoucherDto(
        String code,
        long value,          // gia tri giam (VND)
        Instant redeemedAt,  // ngay doi
        Instant expiresAt,   // han su dung
        boolean used) {      // da dung (co don CONFIRMED dung ma nay) hay chua
}
