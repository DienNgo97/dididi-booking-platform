package com.dididi.booking.identity.service;

import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.notification.EmailService;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Xác thực SỐ ĐIỆN THOẠI bằng OTP 6 số (giống {@link OtpLoginService}).
 * OTP lưu Redis theo userId, hiệu lực 5 phút, giới hạn số lần thử để chống dò.
 *
 * <p>DEMO: do đồ án chưa tích hợp cổng SMS, OTP được gửi qua EMAIL của user (và in log).
 * Khi lên production chỉ cần thay {@code deliver(...)} bằng client SMS (Twilio/eSMS/Viettel...).
 */
@Service
public class PhoneVerificationService {

    private static final String OTP_KEY = "otp:phone:";
    private static final String TRY_KEY = "otp:phone:try:";
    private static final int TTL_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RND = new SecureRandom();

    /** SĐT VN: bắt đầu bằng 0 hoặc +84, 9–11 chữ số. */
    private static final Pattern PHONE = Pattern.compile("^(0|\\+84)\\d{8,10}$");

    private final StringRedisTemplate redis;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public PhoneVerificationService(StringRedisTemplate redis, UserRepository userRepository,
                                    EmailService emailService) {
        this.redis = redis;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    public static boolean isValidPhone(String phone) {
        return phone != null && PHONE.matcher(phone.trim()).matches();
    }

    /** Sinh + gửi OTP xác thực SĐT cho user. */
    public void request(User user) {
        String code = String.format("%06d", RND.nextInt(1_000_000));
        redis.opsForValue().set(OTP_KEY + user.getId(), code, Duration.ofMinutes(TTL_MINUTES));
        redis.delete(TRY_KEY + user.getId());
        // DEMO: gửi mã qua email user (thay bằng SMS ở production).
        if (user.getEmail() != null) {
            emailService.sendLoginOtp(user.getEmail(), code, LocaleContextHolder.getLocale());
        }
    }

    /** true nếu OTP đúng & còn hạn & chưa quá số lần thử; tiêu thụ OTP khi đúng. */
    public boolean verify(Long userId, String code) {
        if (userId == null || code == null) return false;
        String stored = redis.opsForValue().get(OTP_KEY + userId);
        if (stored == null) return false; // hết hạn hoặc chưa yêu cầu
        Long attempts = redis.opsForValue().increment(TRY_KEY + userId);
        if (attempts != null && attempts == 1L) {
            redis.expire(TRY_KEY + userId, Duration.ofMinutes(TTL_MINUTES));
        }
        if (attempts != null && attempts > MAX_ATTEMPTS) {
            redis.delete(OTP_KEY + userId);
            redis.delete(TRY_KEY + userId);
            return false;
        }
        if (!stored.equals(code.trim())) return false;
        redis.delete(OTP_KEY + userId);
        redis.delete(TRY_KEY + userId);
        return true;
    }
}
