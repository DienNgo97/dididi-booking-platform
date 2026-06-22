package com.dididi.booking.vendor.api.dto;

import java.time.LocalDate;
import java.util.List;

/** Thống kê tồn kho 1 loại phòng trong khoảng ngày. */
public record RoomInventoryStatDto(
        Long roomTypeId,
        String name,
        int totalRooms,
        long roomNightsBooked,
        long roomNightsCapacity,
        double occupancyPct,
        int soldOutDays,
        List<LocalDate> lowDays) {
}
