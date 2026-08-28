package com.dididi.booking.settlement.domain;

import com.dididi.booking.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

/**
 * P1-8 — BÚT TOÁN ĐIỀU CHỈNH KỲ SAU.
 *
 * <p>Kỳ đối soát đã CLOSED/PAID là số bất biến: đã in ra, đã đối chiếu, đã chuyển tiền. Nhưng khách
 * vẫn có thể được hoàn tiền một đơn thuộc kỳ đó (khiếu nại muộn, quyết định của admin). Trước đây
 * số đã chốt giữ nguyên và KHÔNG có gì bù lại — đối tác bị thu thừa vĩnh viễn phần hoa hồng của
 * một đơn không còn tồn tại.</p>
 *
 * <p>Nay mỗi lần đó sinh một dòng điều chỉnh, và kỳ chưa chốt kế tiếp sẽ trừ đi khoản này —
 * đúng cách kế toán xử lý: không sửa sổ cũ, ghi bù vào sổ mới.</p>
 */
@Entity
@Table(name = "settlement_adjustment",
        uniqueConstraints = @UniqueConstraint(name = "uk_adjustment_booking", columnNames = "booking_id"))
public class SettlementAdjustment extends BaseEntity {

    @Column(name = "partner_code", nullable = false, length = 32)
    private String partnerCode;

    /** Kỳ gốc của đơn (kỳ đã chốt) — để giải thích cho đối tác khoản trừ này từ đâu ra. */
    @Column(name = "origin_period", nullable = false, length = 7)
    private String originPeriod;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "booking_code", length = 32)
    private String bookingCode;

    /** Doanh thu gộp bị rút lại (số dương). */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal gross = BigDecimal.ZERO;

    /** Phần phải trả đối tác bị rút lại = gross - hoa hồng (số dương). */
    @Column(name = "net_payable", nullable = false, precision = 18, scale = 2)
    private BigDecimal netPayable = BigDecimal.ZERO;

    @Column(length = 255)
    private String reason;

    /** Kỳ đã hấp thụ khoản điều chỉnh này; null = còn treo, sẽ trừ vào kỳ chốt tiếp theo. */
    @Column(name = "applied_period", length = 7)
    private String appliedPeriod;

    public String getPartnerCode() { return partnerCode; }
    public void setPartnerCode(String partnerCode) { this.partnerCode = partnerCode; }
    public String getOriginPeriod() { return originPeriod; }
    public void setOriginPeriod(String originPeriod) { this.originPeriod = originPeriod; }
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public String getBookingCode() { return bookingCode; }
    public void setBookingCode(String bookingCode) { this.bookingCode = bookingCode; }
    public BigDecimal getGross() { return gross; }
    public void setGross(BigDecimal gross) { this.gross = gross; }
    public BigDecimal getNetPayable() { return netPayable; }
    public void setNetPayable(BigDecimal netPayable) { this.netPayable = netPayable; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getAppliedPeriod() { return appliedPeriod; }
    public void setAppliedPeriod(String appliedPeriod) { this.appliedPeriod = appliedPeriod; }
}
