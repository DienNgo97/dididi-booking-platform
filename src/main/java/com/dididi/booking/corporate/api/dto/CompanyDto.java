package com.dididi.booking.corporate.api.dto;

import com.dididi.booking.corporate.domain.entity.Company;

import java.math.BigDecimal;

public record CompanyDto(
        Long id, String name, String code,
        BigDecimal budgetTotal, BigDecimal budgetUsed, BigDecimal remaining,
        String contactEmail, String taxCode, String address,
        BigDecimal approvalThreshold, boolean active) {

    public static CompanyDto from(Company c) {
        return new CompanyDto(c.getId(), c.getName(), c.getCode(),
                c.getBudgetTotal(), c.getBudgetUsed(), c.remaining(),
                c.getContactEmail(), c.getTaxCode(), c.getAddress(),
                c.getApprovalThreshold(), c.isActive());
    }
}
