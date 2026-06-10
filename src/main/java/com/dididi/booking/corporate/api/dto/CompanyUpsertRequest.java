package com.dididi.booking.corporate.api.dto;

import java.math.BigDecimal;

public record CompanyUpsertRequest(
        String name, String code, BigDecimal budgetTotal,
        String contactEmail, String taxCode, String address,
        BigDecimal approvalThreshold, Boolean active) {
}
