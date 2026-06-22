package com.dididi.booking.identity.service;

import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.entity.UserToken;
import com.dididi.booking.identity.domain.enums.Role;
import com.dididi.booking.identity.domain.enums.TokenPurpose;
import com.dididi.booking.identity.domain.enums.UserStatus;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.identity.repository.UserTokenRepository;
import com.dididi.booking.notification.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Đăng ký + kích hoạt qua email + quên/đặt lại mật khẩu.
 * Chính sách mật khẩu: tối thiểu 8 ký tự, có ít nhất 1 chữ hoa, 1 chữ thường, 1 số, 1 ký tự đặc biệt.
 * Khi đổi mật khẩu: không cho trùng mật khẩu gần nhất + đăng xuất khỏi mọi thiết bị/trình duyệt.
 */
@Service
public class AccountService {

    /** Tối thiểu 8 ký tự + đủ 4 nhóm. */
    private static final Pattern STRONG =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$");

    public static final String PASSWORD_RULE_MSG =
            "Mật khẩu phải tối thiểu 8 ký tự, gồm ít nhất 1 chữ in hoa, 1 chữ thường, 1 số và 1 ký tự đặc biệt";

    private final UserRepository userRepository;
    private final UserTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final RefreshTokenService refreshTokenService;
    private final SessionRegistry sessionRegistry;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${app.verification.ttl-hours:24}")
    private long verifyTtlHours;

    @Value("${app.reset.ttl-minutes:60}")
    private long resetTtlMinutes;

    public AccountService(UserRepository userRepository, UserTokenRepository tokenRepository,
                          PasswordEncoder passwordEncoder, EmailService emailService,
                          RefreshTokenService refreshTokenService, SessionRegistry sessionRegistry) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.refreshTokenService = refreshTokenService;
        this.sessionRegistry = sessionRegistry;
    }

    public static boolean isStrong(String pw) {
        return pw != null && STRONG.matcher(pw).matches();
    }

    /**
     * Tạo tài khoản CUSTOMER ở trạng thái INACTIVE và gửi email kích hoạt.
     * Ném BusinessException nếu email đã tồn tại hoặc mật khẩu yếu.
     */
    @Transactional
    public User registerCustomer(String email, String rawPassword, String fullName) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("EMAIL_EXISTS", "Email đã được đăng ký", HttpStatus.CONFLICT);
        }
        if (!isStrong(rawPassword)) {
            throw new BusinessException("WEAK_PASSWORD", PASSWORD_RULE_MSG, HttpStatus.BAD_REQUEST);
        }
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(rawPassword));
        u.setFullName(fullName);
        u.setRole(Role.CUSTOMER);
        u.setStatus(UserStatus.INACTIVE);
        userRepository.save(u);

        String token = issue(u.getId(), TokenPurpose.VERIFY_EMAIL, Duration.ofHours(verifyTtlHours));
        emailService.sendVerification(email, baseUrl + "/verify?token=" + token, LocaleContextHolder.getLocale());
        return u;
    }

    /** Kích hoạt tài khoản từ token email. true nếu thành công. */
    @Transactional
    public boolean verifyEmail(String token) {
        UserToken t = validToken(token, TokenPurpose.VERIFY_EMAIL);
        if (t == null) return false;
        User u = userRepository.findById(t.getUserId()).orElse(null);
        if (u == null) return false;
        if (u.getStatus() == UserStatus.INACTIVE) {
            u.setStatus(UserStatus.ACTIVE);
            userRepository.save(u);
        }
        t.setUsedAt(Instant.now());
        tokenRepository.save(t);
        return true;
    }

    /** Gửi email đặt lại mật khẩu nếu email tồn tại (không tiết lộ email có tồn tại hay không). */
    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email).ifPresent(u -> {
            String token = issue(u.getId(), TokenPurpose.RESET_PASSWORD, Duration.ofMinutes(resetTtlMinutes));
            emailService.sendPasswordReset(email, baseUrl + "/reset-password?token=" + token, LocaleContextHolder.getLocale());
        });
    }

    /** Token đặt lại mật khẩu còn hợp lệ? (để hiển thị form). */
    public boolean isResetTokenValid(String token) {
        return validToken(token, TokenPurpose.RESET_PASSWORD) != null;
    }

    /**
     * Đặt mật khẩu mới từ token. true nếu thành công.
     * - Không cho trùng với mật khẩu gần nhất (mật khẩu hiện tại).
     * - Sau khi đổi: đăng xuất khỏi mọi thiết bị/trình duyệt.
     */
    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        if (!isStrong(newPassword)) {
            throw new BusinessException("WEAK_PASSWORD", PASSWORD_RULE_MSG, HttpStatus.BAD_REQUEST);
        }
        UserToken t = validToken(token, TokenPurpose.RESET_PASSWORD);
        if (t == null) return false;
        User u = userRepository.findById(t.getUserId()).orElse(null);
        if (u == null) return false;

        if (passwordEncoder.matches(newPassword, u.getPasswordHash())) {
            throw new BusinessException("PASSWORD_REUSED",
                    "Mật khẩu mới không được trùng với mật khẩu gần nhất", HttpStatus.BAD_REQUEST);
        }

        u.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(u);
        t.setUsedAt(Instant.now());
        tokenRepository.save(t);

        logoutEverywhere(u);
        return true;
    }

    // ---------------- helpers ----------------

    /** Đăng xuất khỏi mọi thiết bị/trình duyệt (refresh token + access token JWT + session web). */
    private void logoutEverywhere(User u) {
        refreshTokenService.revokeAllForUser(u.getId());           // token API (refresh)
        refreshTokenService.invalidateAccessTokensBefore(u.getId()); // access token JWT cũ hết hiệu lực ngay
        expireWebSessions(u.getEmail());                            // session web (Thymeleaf)
    }

    private void expireWebSessions(String email) {
        if (email == null) return;
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            String username = (principal instanceof UserDetails ud) ? ud.getUsername() : String.valueOf(principal);
            if (email.equals(username)) {
                for (SessionInformation si : sessionRegistry.getAllSessions(principal, false)) {
                    si.expireNow();
                }
            }
        }
    }

    private UserToken validToken(String token, TokenPurpose purpose) {
        if (token == null || token.isBlank()) return null;
        UserToken t = tokenRepository.findByTokenAndPurpose(token, purpose).orElse(null);
        if (t == null || t.getUsedAt() != null || t.getExpiresAt().isBefore(Instant.now())) return null;
        return t;
    }

    private String issue(Long userId, TokenPurpose purpose, Duration ttl) {
        UserToken t = new UserToken();
        t.setUserId(userId);
        t.setToken(UUID.randomUUID().toString().replace("-", ""));
        t.setPurpose(purpose);
        t.setExpiresAt(Instant.now().plus(ttl));
        tokenRepository.save(t);
        return t.getToken();
    }
}
