package com.dididi.booking.wallet.domain.entity;

import com.dididi.booking.common.domain.BaseEntity;
import com.dididi.booking.wallet.domain.enums.PayoutStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Yêu cầu rút tiền của vendor. Khi ở REQUESTED/PROCESSING, số tiền bị GIỮ CHỖ
 * (trừ khỏi khả dụng khi tính toán) nhưng CHƯA có bút toán — bút toán PAYOUT chỉ sinh khi PAID.
 * FAILED/CANCELLED không để lại dấu vết trong sổ cái, tiền tự nhả về khả dụng.
 *
 * STK ngân hàng là SNAPSHOT tại thời điểm tạo yêu cầu — vendor đổi STK sau đó
 * không ảnh hưởng yêu cầu đang chạy (tiền đi đúng tài khoản đã cam kết).
 */
@Entity
@Table(name = "payout_request",
        indexes = {
                @Index(name = "idx_payout_vendor", columnList = "vendor_id"),
                @Index(name = "idx_payout_status", columnList = "status")
        })
public class PayoutRequest extends BaseEntity {

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "VND";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private PayoutStatus status = PayoutStatus.REQUESTED;

    // ---- Snapshot tài khoản nhận tiền tại thời điểm tạo ----
    @Column(name = "bank_name", nullable = false, length = 120)
    private String bankName;

    @Column(name = "bank_account_no", nullable = false, length = 40)
    private String bankAccountNo;

    @Column(name = "bank_account_holder", nullable = false, length = 120)
    private String bankAccountHolder;

    /** Mã giao dịch phía "ngân hàng" (mock: MOCK-...; prod: mã CK admin nhập). */
    @Column(name = "transaction_ref", length = 60)
    private String transactionRef;

    @Column(name = "fail_reason", length = 300)
    private String failReason;

    /** Thời điểm chốt PAID/FAILED/CANCELLED. */
    @Column(name = "processed_at")
    private Instant processedAt;

    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public PayoutStatus getStatus() { return status; }
    public void setStatus(PayoutStatus status) { this.status = status; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getBankAccountNo() { return bankAccountNo; }
    public void setBankAccountNo(String bankAccountNo) { this.bankAccountNo = bankAccountNo; }
    public String getBankAccountHolder() { return bankAccountHolder; }
    public void setBankAccountHolder(String bankAccountHolder) { this.bankAccountHolder = bankAccountHolder; }
    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }
    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
