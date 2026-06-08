package com.dididi.booking.admin.api.dto;

import java.math.BigDecimal;
import java.util.List;

/** Số liệu tổng quan cho dashboard admin (Phase 4b). */
public record DashboardStatsDto(
        long totalUsers,
        long totalHotels,
        long totalFlights,
        long totalBookings,
        long bookingsPendingPayment,
        long bookingsConfirmed,
        long bookingsCancelled,
        long bookingsFailed,
        BigDecimal totalRevenue,
        List<AdminBookingDto> recentBookings) {
}
