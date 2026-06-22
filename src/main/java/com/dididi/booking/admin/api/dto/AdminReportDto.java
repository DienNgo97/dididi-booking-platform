package com.dididi.booking.admin.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Kết quả một báo cáo.
 * kind = "REVENUE" (doanh thu) hoặc "COUNT" (số lượng).
 * Với REVENUE: totalRevenue có giá trị, totalCount = tổng số đơn.
 * Với COUNT:   totalRevenue = null,     totalCount = tổng số lượng mới.
 */
public record AdminReportDto(
        String metric,
        String granularity,
        String kind,
        String currency,
        BigDecimal totalRevenue,
        long totalCount,
        List<AdminReportPointDto> series) {
}
