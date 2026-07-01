package com.dididi.booking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Dang ky {@link RateLimitingFilter} CHI cho cac duong dan /api/auth/* va chay som nhat
 * (truoc Spring Security) de chan brute-force som. So request/phut/IP cau hinh o
 * app.rate-limit.auth.requests-per-minute (mac dinh 10).
 */
@Configuration
public class RateLimitConfig {

    @Bean
    public FilterRegistrationBean<RateLimitingFilter> rateLimitingFilter(
            @Value("${app.rate-limit.auth.requests-per-minute:10}") int requestsPerMinute,
            @Value("${app.rate-limit.trust-forwarded-for:false}") boolean trustForwardedFor) {
        FilterRegistrationBean<RateLimitingFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new RateLimitingFilter(requestsPerMinute, trustForwardedFor));
        // Chong brute-force CA API ln WEB (form login Thymeleaf) — truoc day bo sot /login, /register web.
        reg.addUrlPatterns("/api/auth/*", "/login", "/register", "/forgot-password");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.setName("rateLimitingFilter");
        return reg;
    }
}
