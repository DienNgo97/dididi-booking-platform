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
 * Co default secret de context test khong vo khi thieu config; PROD bat buoc override bang ENV.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long accessTokenMinutes;
    private final String issuer;

    public JwtService(
            @Value("${app.jwt.secret:dev-fallback-secret-please-override-in-prod-min-32-bytes!!}") String secret,
            @Value("${app.jwt.access-token-minutes:60}") long accessTokenMinutes,
            @Value("${app.jwt.issuer:dididi-booking-platform}") String issuer) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
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
