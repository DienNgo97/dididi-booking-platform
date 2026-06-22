package com.dididi.booking.identity.domain.entity;

import com.dididi.booking.common.domain.BaseEntity;
import com.dididi.booking.identity.domain.enums.TokenPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Token gửi qua email: kích hoạt tài khoản (VERIFY_EMAIL) hoặc đặt lại mật khẩu (RESET_PASSWORD).
 * Dùng 1 lần (usedAt) + có hạn (expiresAt).
 */
@Entity
@Table(name = "user_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_tokens_token", columnNames = "token"),
        indexes = @Index(name = "idx_user_tokens_user", columnList = "user_id"))
public class UserToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TokenPurpose purpose;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public TokenPurpose getPurpose() { return purpose; }
    public void setPurpose(TokenPurpose purpose) { this.purpose = purpose; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getUsedAt() { return usedAt; }
    public void setUsedAt(Instant usedAt) { this.usedAt = usedAt; }
}
