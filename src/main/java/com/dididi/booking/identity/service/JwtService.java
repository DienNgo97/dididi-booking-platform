package com.dididi.booking.identity.service;

import com.dididi.booking.identity.domain.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * Sinh va xac thuc JWT (HS256). Cau hinh o app.jwt.* trong application.yml.
 * SEC-03: KHONG con default secret. Thieu/qua ngan (<32 byte) -> fail-fast khi khoi dong
 * (constructor nem IllegalStateException) de tranh ky bang khoa cong khai/yeu o moi truong that.
 */
@Service
public class JwtService {

    /** HS256 yeu cau khoa toi thieu 256-bit = 32 byte. */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long accessTokenMinutes;
    private final String issuer;

    public JwtService(
            @Value("${app.jwt.secret:}") String secret,
            @Value("${app.jwt.access-token-minutes:60}") long accessTokenMinutes,
            @Value("${app.jwt.issuer:dididi-booking-platform}") String issuer) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "app.jwt.secret chua duoc cau hinh. Hay dat bien moi truong JWT_SECRET "
                            + "(it nhat " + MIN_SECRET_BYTES + " byte) truoc khi khoi dong.");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret qua ngan (" + secretBytes.length + " byte); HS256 yeu cau toi thieu "
                            + MIN_SECRET_BYTES + " byte.");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.accessTokenMinutes = accessTokenMinutes;
        this.issuer = issuer;
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant exp = now.plus(accessTokenMinutes, ChronoUnit.MINUTES);
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .id(UUID.randomUUID().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("vendorId", user.getVendorId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    public boolean validate(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
