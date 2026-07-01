package com.dididi.booking.payment.domain.entity;

import com.dididi.booking.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Nhật ký APPEND-ONLY mọi lần VNPay gọi về (return của trình duyệt + IPN server-to-server).
 * Trước đây chỉ có 1 dòng Payment bị ghi đè mỗi lần thử lại -> mất dấu vết. Bảng này giữ tham số thô,
 * mã phản hồi, số tiền, chữ ký hợp lệ? để ĐỐI SOÁT với cổng và điều tra tranh chấp.
 */
@Entity
@Table(name = "payment_attempts", indexes = {
        @Index(name = "idx_pa_booking", columnList = "booking_id"),
        @Index(name = "idx_pa_txnref", columnList = "txn_ref")
})
public class PaymentAttempt extends BaseEntity {

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "txn_ref", length = 64)
    private String txnRef;

    @Column(length = 10)
    private String direction;     // RETURN | IPN

    @Column(name = "response_code", length = 8)
    private String responseCode;

    @Column(precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "signature_valid")
    private Boolean signatureValid;

    @Column(name = "raw_params", length = 2000)
    private String rawParams;

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public String getTxnRef() { return txnRef; }
    public void setTxnRef(String txnRef) { this.txnRef = txnRef; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getResponseCode() { return responseCode; }
    public void setResponseCode(String responseCode) { this.responseCode = responseCode; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Boolean getSignatureValid() { return signatureValid; }
    public void setSignatureValid(Boolean signatureValid) { this.signatureValid = signatureValid; }
    public String getRawParams() { return rawParams; }
    public void setRawParams(String rawParams) { this.rawParams = rawParams; }
}
