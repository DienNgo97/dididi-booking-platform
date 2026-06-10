package com.dididi.booking.gateway.domain;

import com.dididi.booking.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Cau hinh cong thanh toan (VNPay) luu DB - sua qua UI Super Admin. */
@Entity
@Table(name = "payment_gateway_config",
        uniqueConstraints = @UniqueConstraint(name = "uk_gateway_provider", columnNames = "provider"))
public class PaymentGatewayConfig extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String provider = "VNPAY";

    @Column(name = "tmn_code", length = 50)
    private String tmnCode;

    @Column(name = "hash_secret", length = 255)
    private String hashSecret;

    @Column(name = "pay_url", length = 300)
    private String payUrl;

    @Column(name = "return_url", length = 300)
    private String returnUrl;

    @Column(nullable = false)
    private boolean enabled = true;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getTmnCode() { return tmnCode; }
    public void setTmnCode(String tmnCode) { this.tmnCode = tmnCode; }
    public String getHashSecret() { return hashSecret; }
    public void setHashSecret(String hashSecret) { this.hashSecret = hashSecret; }
    public String getPayUrl() { return payUrl; }
    public void setPayUrl(String payUrl) { this.payUrl = payUrl; }
    public String getReturnUrl() { return returnUrl; }
    public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
