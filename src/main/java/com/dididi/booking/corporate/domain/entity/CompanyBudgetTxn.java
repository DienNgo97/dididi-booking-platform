package com.dididi.booking.corporate.domain.entity;

import com.dididi.booking.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Sổ cái biến động ngân sách công ty: mỗi lần trừ (CHARGE) / hoàn (RELEASE) gắn với 1 booking.
 * Trước đây chỉ có 1 con số {@code budgetUsed} -> không truy được biến động. Bảng này cho phép đối soát.
 */
@Entity
@Table(name = "company_budget_txns", indexes = {
        @Index(name = "idx_cbt_company", columnList = "company_id"),
        @Index(name = "idx_cbt_booking", columnList = "booking_id")
})
public class CompanyBudgetTxn extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "booking_id")
    private Long bookingId;

    /** Số tiền (dương). type cho biết là CHARGE hay RELEASE. */
    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(length = 20, nullable = false)
    private String type;   // CHARGE | RELEASE

    @Column(length = 255)
    private String note;

    public CompanyBudgetTxn() {}

    public CompanyBudgetTxn(Long companyId, Long bookingId, BigDecimal amount, String type, String note) {
        this.companyId = companyId;
        this.bookingId = bookingId;
        this.amount = amount;
        this.type = type;
        this.note = note;
    }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
