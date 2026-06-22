package com.dididi.booking.identity.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;

/**
 * Quan ly refresh token bang Redis (Phase Dot1 #3).
 *  - issue: sinh token ngau nhien, luu Redis key "refresh:{token}" -> userId, het han sau N ngay.
 *           dong thoi them vao set "refresh:user:{userId}" de co the thu hoi tat ca token cua 1 user.
 *  - userIdOf: tra userId neu token con hieu luc (chua het han / chua bi thu hoi).
 *  - revoke: xoa token (dung khi logout).
 *  - rotate: thu hoi token cu + phat token moi (chong dung lai refresh token da xai).
 *  - revokeAllForUser: thu hoi MOI refresh token cua user (dang xuat moi thiet bi).
 *  - invalidateAccessTokensBefore / isAccessTokenStillValid: moc vo hieu access token JWT cu
 *    (dung khi doi mat khau de tat tat ca phien dang nhap ngay lap tuc).
 */
@Service
public class RefreshTokenService {

    private static final String PREFIX = "refresh:";
    private static final String USER_INDEX = "refresh:user:";   // set: userId -> {tokens}
    private static final String PW_CHANGED = "pwchanged:";       // userId -> epoch giay (moc vo hieu access token cu)
    private static final SecureRandom RND = new SecureRandom();

    private final StringRedisTemplate redis;
    private final long refreshDays;
    private final long accessTokenMinutes;

    public RefreshTokenService(StringRedisTemplate redis,
                               @Value("${app.jwt.refresh-token-days:7}") long refreshDays,
                               @Value("${app.jwt.access-token-minutes:60}") long accessTokenMinutes) {
        this.redis = redis;
        this.refreshDays = refreshDays;
        this.accessTokenMinutes = accessTokenMinutes;
    }

    public String issue(Long userId) {
        byte[] buf = new byte[32];
        RND.nextBytes(buf);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        redis.opsForValue().set(PREFIX + token, String.valueOf(userId), Duration.ofDays(refreshDays));
        // index nguoc: de thu hoi tat ca token cua 1 user
        String idxKey = USER_INDEX + userId;
        redis.opsForSet().add(idxKey, token);
        redis.expire(idxKey, Duration.ofDays(refreshDays));
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
        if (token == null || token.isBlank()) return;
        Long userId = userIdOf(token);
        redis.delete(PREFIX + token);
        if (userId != null) {
            redis.opsForSet().remove(USER_INDEX + userId, token);
        }
    }

    public String rotate(Long userId, String oldToken) {
        revoke(oldToken);
        return issue(userId);
    }

    /** Thu hoi TAT CA refresh token cua 1 user (dang xuat moi thiet bi dung API). */
    public void revokeAllForUser(Long userId) {
        if (userId == null) return;
        String idxKey = USER_INDEX + userId;
        Set<String> tokens = redis.opsForSet().members(idxKey);
        if (tokens != null) {
            for (String t : tokens) {
                redis.delete(PREFIX + t);
            }
        }
        redis.delete(idxKey);
    }

    /** Dat moc: moi access token (JWT) phat hanh TRUOC thoi diem nay deu bi coi la het hieu luc. */
    public void invalidateAccessTokensBefore(Long userId) {
        if (userId == null) return;
        long nowSec = Instant.now().getEpochSecond();
        // chi can giu toi khi token cu chac chan da het han
        redis.opsForValue().set(PW_CHANGED + userId, String.valueOf(nowSec),
                Duration.ofMinutes(accessTokenMinutes + 1));
    }

    /** Access token con hieu luc khong (so iat voi moc doi mat khau). */
    public boolean isAccessTokenStillValid(String userId, long issuedAtEpochSeconds) {
        if (userId == null) return true;
        String v = redis.opsForValue().get(PW_CHANGED + userId);
        if (v == null) return true;
        try {
            return issuedAtEpochSeconds >= Long.parseLong(v);
        } catch (NumberFormatException e) {
            return true;
        }
    }

    public long refreshDays() {
        return refreshDays;
    }
}
