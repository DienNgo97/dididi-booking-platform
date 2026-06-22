package com.dididi.booking.identity.service;

import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.UserStatus;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.notification.EmailService;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * Đăng nhập bằng OTP gửi qua email (giống Agoda).
 * OTP 6 số, hiệu lực 5 phút, lưu Redis; giới hạn số lần thử để chống dò.
 */
@Service
public class OtpLoginService {

    public enum RequestResult { SENT, INACTIVE, NOT_FOUND }

    private static final String OTP_KEY = "otp:login:";
    private static final String TRY_KEY = "otp:login:try:";
    private static final int TTL_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RND = new SecureRandom();

    private final StringRedisTemplate redis;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public OtpLoginService(StringRedisTemplate redis, UserRepository userRepository, EmailService emailService) {
        this.redis = redis;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    /** Sinh + gửi OTP. Chỉ gửi cho user ACTIVE. */
    public RequestResult request(String email) {
        if (email == null || email.isBlank()) return RequestResult.NOT_FOUND;
        String key = email.trim();
        User u = userRepository.findByEmail(key).orElse(null);
        if (u == null) return RequestResult.NOT_FOUND;
        if (u.getStatus() != UserStatus.ACTIVE) return RequestResult.INACTIVE;
        String code = String.format("%06d", RND.nextInt(1_000_000));
        redis.opsForValue().set(OTP_KEY + key, code, Duration.ofMinutes(TTL_MINUTES));
        redis.delete(TRY_KEY + key);
        emailService.sendLoginOtp(key, code, LocaleContextHolder.getLocale());
        return RequestResult.SENT;
    }

    /** true nếu OTP đúng & còn hạn & chưa quá số lần thử; tiêu thụ OTP khi đúng. */
    public boolean verify(String email, String code) {
        if (email == null || code == null) return false;
        String key = email.trim();
        String stored = redis.opsForValue().get(OTP_KEY + key);
        if (stored == null) return false; // hết hạn hoặc chưa yêu cầu
        Long attempts = redis.opsForValue().increment(TRY_KEY + key);
        if (attempts != null && attempts == 1L) {
            redis.expire(TRY_KEY + key, Duration.ofMinutes(TTL_MINUTES));
        }
        if (attempts != null && attempts > MAX_ATTEMPTS) {
            redis.delete(OTP_KEY + key);
            redis.delete(TRY_KEY + key);
            return false;
        }
        if (!stored.equals(code.trim())) return false;
        redis.delete(OTP_KEY + key);
        redis.delete(TRY_KEY + key);
        return true;
    }
}
