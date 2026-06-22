package com.dididi.booking.vendor.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Báo cáo doanh thu cho 1 mức (TOTAL/WEEK/MONTH/YEAR).
 * byTier + bySegment: cả hai cách nhóm khách (frontend gạt qua lại, không cần gọi API lại).
 */
public record RevenueReportDto(
        String granularity,
        String currency,
        BigDecimal totalRevenue,
        long bookingCount,
        long roomNights,
        BigDecimal avgOrderValue,
        List<SeriesPointDto> series,
        List<RoomTypeRevenueDto> byRoomType,
        GroupPreferenceDto byTier,
        GroupPreferenceDto bySegment) {
}
