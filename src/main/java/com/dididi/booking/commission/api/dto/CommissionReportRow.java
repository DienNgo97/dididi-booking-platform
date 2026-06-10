package com.dididi.booking.commission.api.dto;

import java.math.BigDecimal;

public record CommissionReportRow(
        Long vendorId, String vendorName, long bookingCount,
        BigDecimal gross, BigDecimal rate, BigDecimal commission, BigDecimal net) {}
