package com.dididi.booking.vendor.api.dto;

import java.math.BigDecimal;
import java.util.List;

/** Số liệu tổng quan cho trang dashboard vendor. */
public record VendorDashboardDto(
        String currency,
        BigDecimal thisMonthRevenue,
        BigDecimal lastMonthRevenue,
        double revenueChangePct,
        long thisMonthBookings,
        BigDecimal avgOrderValue,
        double occupancyNext30Pct,
        double avgRating,
        long reviewCount,
        long unansweredReviews,
        List<UpcomingCheckinDto> upcomingCheckins,
        List<RoomTypeRevenueDto> topRoomTypes,
        List<SeriesPointDto> last30Days) {
}
