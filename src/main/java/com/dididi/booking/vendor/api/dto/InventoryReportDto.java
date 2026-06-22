package com.dididi.booking.vendor.api.dto;

import java.time.LocalDate;
import java.util.List;

/** Báo cáo tồn kho cho khoảng [from, to]. */
public record InventoryReportDto(
        LocalDate from,
        LocalDate to,
        long days,
        String currency,
        List<RoomInventoryStatDto> rooms) {
}
