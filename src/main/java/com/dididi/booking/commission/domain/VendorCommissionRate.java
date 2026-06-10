package com.dididi.booking.commission.domain;

import com.dididi.booking.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

/** Ty le hoa hong rieng cho 1 vendor (ghi de mac dinh). */
@Entity
@Table(name = "vendor_commission_rate",
        uniqueConstraints = @UniqueConstraint(name = "uk_vendor_commission", columnNames = "vendor_id"))
public class VendorCommissionRate extends BaseEntity {

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal rate;

    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }
    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }
}
