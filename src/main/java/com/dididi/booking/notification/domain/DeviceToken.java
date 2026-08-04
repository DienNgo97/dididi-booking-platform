package com.dididi.booking.notification.domain;

import com.dididi.booking.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Token thiết bị (FCM) để gửi push cho 1 user. Unique theo token. */
@Entity
@Table(name = "device_token", uniqueConstraints = @UniqueConstraint(columnNames = "token"))
public class DeviceToken extends BaseEntity {

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 300)
    private String token;

    @Column(length = 20)
    private String platform;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
}
