package com.dididi.booking.payment.domain.entity;

import com.dididi.booking.common.domain.BaseEntity;
import com.dididi.booking.payment.domain.enums.PaymentStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency = "VND";

    @Column(length = 20)
    private String method = "MOCK";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "transaction_ref", length = 50)
    private String transactionRef;

    // ---- VNPay (Phase 8a) ----
    @Column(name = "gateway_txn_no", length = 50)
    private String gatewayTxnNo;          // vnp_TransactionNo

    @Column(name = "bank_code", length = 20)
    private String bankCode;              // vnp_BankCode

    @Column(name = "response_code", length = 4)
    private String responseCode;          // vnp_ResponseCode

    @Column(name = "pay_date", length = 14)
    private String payDate;               // vnp_PayDate (yyyyMMddHHmmss)

    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }
    public String getGatewayTxnNo() { return gatewayTxnNo; }
    public void setGatewayTxnNo(String gatewayTxnNo) { this.gatewayTxnNo = gatewayTxnNo; }
    public String getBankCode() { return bankCode; }
    public void setBankCode(String bankCode) { this.bankCode = bankCode; }
    public String getResponseCode() { return responseCode; }
    public void setResponseCode(String responseCode) { this.responseCode = responseCode; }
    public String getPayDate() { return payDate; }
    public void setPayDate(String payDate) { this.payDate = payDate; }
}
