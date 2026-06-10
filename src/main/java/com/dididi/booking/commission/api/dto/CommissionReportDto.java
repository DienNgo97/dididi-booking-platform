package com.dididi.booking.commission.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record CommissionReportDto(
        List<CommissionReportRow> rows,
        BigDecimal totalGross, BigDecimal totalCommission, BigDecimal totalNet) {}
