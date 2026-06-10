package com.dididi.booking.payment.api.dto;

import com.dididi.booking.payment.domain.entity.Refund;

import java.math.BigDecimal;
import java.time.Instant;

public record RefundDto(
        Long id,
        Long bookingId,
        Long paymentId,
        BigDecimal amount,
        String currency,
        String reason,
        String status,
        Long processedBy,
        Instant createdAt) {

    public static RefundDto from(Refund r) {
        return new RefundDto(r.getId(), r.getBookingId(), r.getPaymentId(), r.getAmount(),
                r.getCurrency(), r.getReason(), r.getStatus().name(), r.getProcessedBy(),
                r.getCreatedAt());
    }
}
