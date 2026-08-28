package com.dididi.booking.wallet.domain.entity;

import com.dididi.booking.common.domain.BaseEntity;
import com.dididi.booking.wallet.domain.enums.LedgerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * SỔ CÁI ví vendor — mỗi biến động tiền là MỘT bút toán bất biến, số dư = SUM(netAmount).
 *
 * Nguyên tắc thiết kế (chốt với Jay 19/08):
 * - Rate hoa hồng CHỐT trong bút toán: admin đổi % về sau không làm lệch tiền đã ghi.
 * - Unique (booking_id, type): một đơn chỉ có tối đa 1 EARNING + 1 REVERSAL —
 *   scheduler quét idempotent, chạy chồng/restart không ghi trùng (PAYOUT có bookingId null,
 *   MySQL cho phép nhiều NULL trong unique index nên không vướng).
 * - availableFrom = checkOut + 3 ngày (khớp cửa sổ khiếu nại — quá mốc này hoàn tiền bị chặn
 *   nên tiền khả dụng là tiền KHÔNG THỂ bị đòi lại nữa; ví không bao giờ âm).
 *   REVERSAL sao chép availableFrom của EARNING gốc để cặp bút toán triệt tiêu đúng bucket.
 */
@Entity
@Table(name = "vendor_ledger_entry",
        uniqueConstraints = @UniqueConstraint(name = "uk_ledger_booking_type", columnNames = {"booking_id", "type"}),
        indexes = {
                @Index(name = "idx_ledger_vendor", columnList = "vendor_id"),
                @Index(name = "idx_ledger_vendor_avail", columnList = "vendor_id,available_from")
        })
public class VendorLedgerEntry extends BaseEntity {

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private LedgerType type;

    /** Đơn nguồn (EARNING/REVERSAL); null với PAYOUT. */
    @Column(name = "booking_id")
    private Long bookingId;

    /** Yêu cầu rút nguồn (PAYOUT); null với EARNING/REVERSAL. */
    @Column(name = "payout_id")
    private Long payoutId;

    /** Doanh thu gộp của đơn (EARNING/REVERSAL); null với PAYOUT. */
    @Column(precision = 15, scale = 2)
    private BigDecimal gross;

    /** Rate hoa hồng chốt tại thời điểm ghi (vd 0.1500). */
    @Column(name = "commission_rate", precision = 7, scale = 4)
    private BigDecimal commissionRate;

    @Column(name = "commission_amount", precision = 15, scale = 2)
    private BigDecimal commissionAmount;

    /** Biến động ròng vào ví: EARNING dương, REVERSAL/PAYOUT âm. */
    @Column(name = "net_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal netAmount;

    /** Từ ngày này tiền mới tính vào "khả dụng rút". */
    @Column(name = "available_from", nullable = false)
    private LocalDate availableFrom;

    @Column(length = 300)
    private String note;

    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }
    public LedgerType getType() { return type; }
    public void setType(LedgerType type) { this.type = type; }
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public Long getPayoutId() { return payoutId; }
    public void setPayoutId(Long payoutId) { this.payoutId = payoutId; }
    public BigDecimal getGross() { return gross; }
    public void setGross(BigDecimal gross) { this.gross = gross; }
    public BigDecimal getCommissionRate() { return commissionRate; }
    public void setCommissionRate(BigDecimal commissionRate) { this.commissionRate = commissionRate; }
    public BigDecimal getCommissionAmount() { return commissionAmount; }
    public void setCommissionAmount(BigDecimal commissionAmount) { this.commissionAmount = commissionAmount; }
    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }
    public LocalDate getAvailableFrom() { return availableFrom; }
    public void setAvailableFrom(LocalDate availableFrom) { this.availableFrom = availableFrom; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
