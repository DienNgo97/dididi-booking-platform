package com.dididi.booking.admin.api.dto;

import java.math.BigDecimal;

/**
 * Một điểm dữ liệu trong chuỗi báo cáo.
 * - Báo cáo doanh thu: revenue = tổng doanh thu trong kỳ, count = số đơn.
 * - Báo cáo số lượng (người dùng/vendor mới): revenue = null, count = số lượng mới trong kỳ.
 */
public record AdminReportPointDto(String label, BigDecimal revenue, long count) {
}
