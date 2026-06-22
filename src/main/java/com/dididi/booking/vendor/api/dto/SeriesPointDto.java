package com.dididi.booking.vendor.api.dto;

import java.math.BigDecimal;

/** 1 mốc trên trục thời gian (tuần/tháng/năm/ngày). */
public record SeriesPointDto(String label, BigDecimal revenue, long bookings) {
}
