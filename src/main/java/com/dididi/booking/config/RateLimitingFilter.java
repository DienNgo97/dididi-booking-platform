package com.dididi.booking.config;

import io.github.bucket4j.Bucket;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limit theo IP cho cac endpoint dang nhap / dang ky / refresh (chong do mat khau, spam).
 * Token bucket in-memory bang bucket4j: moi IP co {requestsPerMinute} token,
 * hoi day {requestsPerMinute} token moi phut (greedy - hoi day muot). Vuot -> 429 Too Many Requests.
 * Khong dung Redis (du cho demo 1 node). Han che: map khong tu xoa IP cu -> production nen dung
 * cache co TTL (Caffeine) hoac bucket4j-redis.
 */
public class RateLimitingFilter implements Filter {

    /** Chi gioi han cac duong dan auth nay (method POST). */
    private static final Set<String> LIMITED_SUFFIXES = Set.of("/login", "/register", "/refresh");

    private final int requestsPerMinute;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitingFilter(int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        if (!shouldLimit(req)) {
            chain.doFilter(request, response);
            return;
        }

        String ip = clientIp(req);
        Bucket bucket = buckets.computeIfAbsent(ip, k -> newBucket());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            res.setStatus(429); // 429 Too Many Requests
            res.setContentType("application/json;charset=UTF-8");
            res.setHeader("Retry-After", "60");
            res.getWriter().write(
                    "{\"success\":false,\"data\":null,"
                  + "\"message\":\"Bạn thao tác quá nhanh. Vui lòng thử lại sau ít phút.\"}");
        }
    }

    private boolean shouldLimit(HttpServletRequest req) {
        if (!"POST".equalsIgnoreCase(req.getMethod())) {
            return false;
        }
        String uri = req.getRequestURI();
        for (String suffix : LIMITED_SUFFIXES) {
            if (uri.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(requestsPerMinute)
                        .refillGreedy(requestsPerMinute, Duration.ofMinutes(1)))
                .build();
    }

    /** Lay IP that su; ho tro X-Forwarded-For khi chay sau proxy/ngrok. */
    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return req.getRemoteAddr();
    }
}
