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
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limit theo IP cho cac endpoint dang nhap / dang ky / refresh (chong do mat khau, spam).
 * Token bucket in-memory bang bucket4j: moi IP co {requestsPerMinute} token,
 * hoi day {requestsPerMinute} token moi phut (greedy - hoi day muot). Vuot -> 429 Too Many Requests.
 * Khong dung Redis (du cho demo 1 node).
 *
 * SEC-07:
 *  - Map duoc CHAN KICH THUOC (MAX_BUCKETS) + don dinh ky (CLEANUP_INTERVAL) de khong phinh vo han (OOM / spoof IP).
 *  - Mac dinh lay req.getRemoteAddr() (khong tin X-Forwarded-For — co the gia mao de bypass brute-force);
 *    chi tin first-hop X-Forwarded-For khi app.rate-limit.trust-forwarded-for=true (chay sau proxy tin cay).
 */
public class RateLimitingFilter implements Filter {

    /** Chi gioi han cac duong dan auth nay (method POST). */
    private static final Set<String> LIMITED_SUFFIXES = Set.of("/login", "/register", "/refresh");

    /** Tran so IP theo doi dong thoi: vuot -> xoa sach (don gian, tranh OOM khi bi flood IP gia). */
    private static final int MAX_BUCKETS = 10_000;
    /** Don map sau moi N request bi gioi han. */
    private static final long CLEANUP_INTERVAL = 5_000L;

    private final int requestsPerMinute;
    private final boolean trustForwardedFor;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong sinceCleanup = new AtomicLong(0);

    public RateLimitingFilter(int requestsPerMinute) {
        this(requestsPerMinute, false);
    }

    public RateLimitingFilter(int requestsPerMinute, boolean trustForwardedFor) {
        this.requestsPerMinute = requestsPerMinute;
        this.trustForwardedFor = trustForwardedFor;
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

        maybeCleanup();
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

    /**
     * SEC-07: mac dinh dung getRemoteAddr() (khong the gia mao tu client). Chi doc first-hop X-Forwarded-For
     * khi trustForwardedFor=true (app chay sau proxy/LB tin cay dat header nay).
     */
    private String clientIp(HttpServletRequest req) {
        if (trustForwardedFor) {
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                int comma = xff.indexOf(',');
                return (comma > 0 ? xff.substring(0, comma) : xff).trim();
            }
        }
        return req.getRemoteAddr();
    }

    /** SEC-07: dinh ky / khi vuot tran -> xoa sach map de chan tang truong vo han (chap nhan reset bucket). */
    private void maybeCleanup() {
        if (buckets.size() > MAX_BUCKETS) {
            buckets.clear();
            sinceCleanup.set(0);
            return;
        }
        if (sinceCleanup.incrementAndGet() >= CLEANUP_INTERVAL) {
            sinceCleanup.set(0);
            // Bucket4j khong lo idle-time; don gian nhat la xoa sach dinh ky (token se duoc cap lai khi IP quay lai).
            // An toan cho demo 1 node: chi xoa khi map da kha lon de tranh reset lien tuc.
            if (buckets.size() > MAX_BUCKETS / 2) {
                buckets.clear();
            }
        }
    }
}
