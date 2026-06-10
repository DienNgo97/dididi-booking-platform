package com.dididi.booking.corporate.domain.entity;

import com.dididi.booking.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

/** Cong ty B2B: hanh muc TONG (budgetTotal), da dung (budgetUsed). Con lai = total - used. */
@Entity
@Table(name = "companies", uniqueConstraints = @UniqueConstraint(name = "uk_company_code", columnNames = "code"))
public class Company extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(name = "budget_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal budgetTotal = BigDecimal.ZERO;

    @Column(name = "budget_used", nullable = false, precision = 18, scale = 2)
    private BigDecimal budgetUsed = BigDecimal.ZERO;

    @Column(name = "contact_email", length = 150)
    private String contactEmail;

    /** Ma so thue (MST) - in tren hoa don VAT. */
    @Column(name = "tax_code", length = 30)
    private String taxCode;

    /** Dia chi cong ty - in tren hoa don VAT. */
    @Column(length = 255)
    private String address;

    /** Don tra bang ngan sach > nguong nay -> can phe duyet. Null/0 = khong can duyet. */
    @Column(name = "approval_threshold", precision = 18, scale = 2)
    private BigDecimal approvalThreshold;

    @Column(nullable = false)
    private boolean active = true;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public BigDecimal getBudgetTotal() { return budgetTotal; }
    public void setBudgetTotal(BigDecimal budgetTotal) { this.budgetTotal = budgetTotal; }
    public BigDecimal getBudgetUsed() { return budgetUsed; }
    public void setBudgetUsed(BigDecimal budgetUsed) { this.budgetUsed = budgetUsed; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getTaxCode() { return taxCode; }
    public void setTaxCode(String taxCode) { this.taxCode = taxCode; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public BigDecimal getApprovalThreshold() { return approvalThreshold; }
    public void setApprovalThreshold(BigDecimal approvalThreshold) { this.approvalThreshold = approvalThreshold; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public BigDecimal remaining() {
        BigDecimal t = budgetTotal == null ? BigDecimal.ZERO : budgetTotal;
        BigDecimal u = budgetUsed == null ? BigDecimal.ZERO : budgetUsed;
        return t.subtract(u);
    }
}
