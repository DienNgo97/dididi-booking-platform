package com.dididi.booking.voucher.domain;

import com.dididi.booking.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

/** Ma giam gia (voucher). PERCENT: discountValue=% (vd 10), co the cap maxDiscount. FIXED: discountValue=so tien VND. */
@Entity
@Table(name = "voucher", uniqueConstraints = @UniqueConstraint(name = "uk_voucher_code", columnNames = "code"))
public class Voucher extends BaseEntity {

    @Column(nullable = false, length = 40)
    private String code;

    @Column(length = 200)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 16)
    private VoucherDiscountType discountType = VoucherDiscountType.PERCENT;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue = BigDecimal.ZERO;

    /** Tran giam toi da (chi ap dung cho PERCENT). Null = khong gioi han. */
    @Column(name = "max_discount", precision = 18, scale = 2)
    private BigDecimal maxDiscount;

    /** Gia tri don toi thieu de dung. Null = khong yeu cau. */
    @Column(name = "min_order_amount", precision = 18, scale = 2)
    private BigDecimal minOrderAmount;

    /** Tong luot dung toi da. Null = khong gioi han. */
    @Column(name = "usage_limit")
    private Integer usageLimit;

    /** So luot moi nguoi dung. Null = khong gioi han. */
    @Column(name = "per_user_limit")
    private Integer perUserLimit;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(nullable = false)
    private boolean active = true;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public VoucherDiscountType getDiscountType() { return discountType; }
    public void setDiscountType(VoucherDiscountType discountType) { this.discountType = discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }
    public BigDecimal getMaxDiscount() { return maxDiscount; }
    public void setMaxDiscount(BigDecimal maxDiscount) { this.maxDiscount = maxDiscount; }
    public BigDecimal getMinOrderAmount() { return minOrderAmount; }
    public void setMinOrderAmount(BigDecimal minOrderAmount) { this.minOrderAmount = minOrderAmount; }
    public Integer getUsageLimit() { return usageLimit; }
    public void setUsageLimit(Integer usageLimit) { this.usageLimit = usageLimit; }
    public Integer getPerUserLimit() { return perUserLimit; }
    public void setPerUserLimit(Integer perUserLimit) { this.perUserLimit = perUserLimit; }
    public Instant getValidFrom() { return validFrom; }
    public void setValidFrom(Instant validFrom) { this.validFrom = validFrom; }
    public Instant getValidTo() { return validTo; }
    public void setValidTo(Instant validTo) { this.validTo = validTo; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
