package com.dididi.booking.settlement.domain;

import com.dididi.booking.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * BẢN CHỐT đối soát công nợ B2B với đối tác tích hợp API theo KỲ THÁNG (ST2, 27/08/2026).
 *
 * Khác ví vendor (đối tác nhỏ tự rút): đối tác API (hotel-pms, các hãng bay) không đăng nhập
 * Dididi — nền tảng chủ động chốt kỳ, xuất file đối soát, chuyển khoản B2B rồi ghi nhận đã trả.
 *
 * Chỉ tồn tại bản ghi từ lúc CHỐT KỲ (trước đó số liệu tính live). Khi đã chốt, con số là
 * BẤT BIẾN — được bảo đảm bởi quy tắc: chỉ chốt được kỳ đã kết thúc + 3 ngày (cửa sổ khiếu nại),
 * lúc đó mọi đơn trong kỳ đã hết đường hoàn tiền nên tính lại lúc nào cũng ra đúng số này.
 * Unique (partner_code, period_ym) chống chốt trùng.
 */
@Entity
@Table(name = "partner_settlement",
        uniqueConstraints = @UniqueConstraint(name = "uk_settlement_partner_period",
                columnNames = {"partner_code", "period_ym"}))
public class PartnerSettlement extends BaseEntity {

    public enum Status { CLOSED, PAID }

    /** HOTEL_PMS hoặc mã hãng bay (VN/VJ/QH/BL/VU). */
    @Column(name = "partner_code", nullable = false, length = 20)
    private String partnerCode;

    @Column(name = "partner_name", nullable = false, length = 120)
    private String partnerName;

    /** Kỳ tháng dạng "2026-07". */
    @Column(name = "period_ym", nullable = false, length = 7)
    private String periodYm;

    @Column(name = "booking_count", nullable = false)
    private long bookingCount;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal gross;

    /** Rate hoa hồng nền tảng CHỐT tại thời điểm chốt kỳ. */
    @Column(name = "commission_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal commissionRate;

    @Column(name = "commission_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal commissionAmount;

    /** Công nợ phải trả đối tác = gross − hoa hồng. */
    @Column(name = "net_payable", nullable = false, precision = 18, scale = 2)
    private BigDecimal netPayable;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.CLOSED;

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "paid_by")
    private Long paidBy;

    /** Mã UNC/chuyển khoản khi đánh dấu đã thanh toán. */
    @Column(name = "payment_ref", length = 60)
    private String paymentRef;

    public String getPartnerCode() { return partnerCode; }
    public void setPartnerCode(String partnerCode) { this.partnerCode = partnerCode; }
    public String getPartnerName() { return partnerName; }
    public void setPartnerName(String partnerName) { this.partnerName = partnerName; }
    public String getPeriodYm() { return periodYm; }
    public void setPeriodYm(String periodYm) { this.periodYm = periodYm; }
    public long getBookingCount() { return bookingCount; }
    public void setBookingCount(long bookingCount) { this.bookingCount = bookingCount; }
    public BigDecimal getGross() { return gross; }
    public void setGross(BigDecimal gross) { this.gross = gross; }
    public BigDecimal getCommissionRate() { return commissionRate; }
    public void setCommissionRate(BigDecimal commissionRate) { this.commissionRate = commissionRate; }
    public BigDecimal getCommissionAmount() { return commissionAmount; }
    public void setCommissionAmount(BigDecimal commissionAmount) { this.commissionAmount = commissionAmount; }
    public BigDecimal getNetPayable() { return netPayable; }
    public void setNetPayable(BigDecimal netPayable) { this.netPayable = netPayable; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Long getClosedBy() { return closedBy; }
    public void setClosedBy(Long closedBy) { this.closedBy = closedBy; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
    public Long getPaidBy() { return paidBy; }
    public void setPaidBy(Long paidBy) { this.paidBy = paidBy; }
    public String getPaymentRef() { return paymentRef; }
    public void setPaymentRef(String paymentRef) { this.paymentRef = paymentRef; }
}
