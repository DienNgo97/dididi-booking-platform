package com.dididi.booking.identity.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * Quan ly refresh token bang Redis (Phase Dot1 #3).
 *  - issue: sinh token ngau nhien, luu Redis key "refresh:{token}" -> userId, het han sau N ngay.
 *  - userIdOf: tra userId neu token con hieu luc (chua het han / chua bi thu hoi).
 *  - revoke: xoa token (dung khi logout).
 *  - rotate: thu hoi token cu + phat token moi (chong dung lai refresh token da xai).
 */
@Service
public class RefreshTokenService {

    private static final String PREFIX = "refresh:";
    private static final SecureRandom RND = new SecureRandom();

    private final StringRedisTemplate redis;
    private final long refreshDays;

    public RefreshTokenService(StringRedisTemplate redis,
                               @Value("${app.jwt.refresh-token-days:7}") long refreshDays) {
        this.redis = redis;
        this.refreshDays = refreshDays;
    }

    public String issue(Long userId) {
        byte[] buf = new byte[32];
        RND.nextBytes(buf);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        redis.opsForValue().set(PREFIX + token, String.valueOf(userId), Duration.ofDays(refreshDays));
        return token;
    }

    public Long userIdOf(String token) {
        if (token == null || token.isBlank()) return null;
        String v = redis.opsForValue().get(PREFIX + token);
        if (v == null) return null;
        try {
            return Long.valueOf(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void revoke(String token) {
        if (token != null && !token.isBlank()) {
            redis.delete(PREFIX + token);
        }
    }

    public String rotate(Long userId, String oldToken) {
        revoke(oldToken);
        return issue(userId);
    }

    public long refreshDays() {
        return refreshDays;
    }
}
