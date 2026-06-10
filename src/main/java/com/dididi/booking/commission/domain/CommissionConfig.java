package com.dididi.booking.commission.domain;

import com.dididi.booking.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Cau hinh hoa hong mac dinh toan san (1 dong). */
@Entity
@Table(name = "commission_config")
public class CommissionConfig extends BaseEntity {

    @Column(name = "default_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal defaultRate = new BigDecimal("0.1500");

    public BigDecimal getDefaultRate() { return defaultRate; }
    public void setDefaultRate(BigDecimal defaultRate) { this.defaultRate = defaultRate; }
}
