package com.dididi.booking.vendor.api.dto;

import java.math.BigDecimal;

/** Doanh thu + số đơn theo từng loại phòng. */
public record RoomTypeRevenueDto(Long roomTypeId, String name, BigDecimal revenue, long bookings) {
}
