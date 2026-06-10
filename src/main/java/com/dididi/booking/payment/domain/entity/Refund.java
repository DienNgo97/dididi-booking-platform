package com.dididi.booking.payment.domain.entity;

import com.dididi.booking.common.domain.BaseEntity;
import com.dididi.booking.payment.domain.enums.RefundStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;

/** Ban ghi 1 lan hoan tien cho 1 don (audit). Hien tai chi ho tro hoan TOAN PHAN. */
@Entity
@Table(name = "refunds")
public class Refund extends BaseEntity {

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency = "VND";

    @Column(length = 300)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status = RefundStatus.COMPLETED;

    /** userId cua admin/super-admin thuc hien hoan. */
    @Column(name = "processed_by")
    private Long processedBy;

    /** Ma giao dich hoan ben cong (neu sau nay goi API refund VNPay). */
    @Column(name = "gateway_refund_no", length = 50)
    private String gatewayRefundNo;

    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public RefundStatus getStatus() { return status; }
    public void setStatus(RefundStatus status) { this.status = status; }
    public Long getProcessedBy() { return processedBy; }
    public void setProcessedBy(Long processedBy) { this.processedBy = processedBy; }
    public String getGatewayRefundNo() { return gatewayRefundNo; }
    public void setGatewayRefundNo(String gatewayRefundNo) { this.gatewayRefundNo = gatewayRefundNo; }
}
