package com.dididi.booking.commission.api.dto;

import java.math.BigDecimal;

public record VendorCommissionDto(Long vendorId, String vendorName, String vendorEmail, BigDecimal rate) {}
