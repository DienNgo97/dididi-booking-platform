package com.dididi.booking.approval.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ApprovalRequestDto(
        Long id, String bookingCode, String bookingTitle, Long companyId, String companyName,
        String requestedByEmail, BigDecimal amount, String status, String decisionNote, Instant createdAt) {
}
