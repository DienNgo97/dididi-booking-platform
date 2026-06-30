package com.dididi.booking.identity.service;

import com.dididi.booking.audit.event.AuditEvent;
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
import org.springframework.context.ApplicationEventPublisher;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    /** Thông tin 1 phiên đăng nhập web (cho trang quản lý thiết bị). */
    public record WebSessionInfo(String sessionId, Instant lastRequest, boolean current) {}

    private final UserRepository userRepository;
    private final UserTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final RefreshTokenService refreshTokenService;
    private final SessionRegistry sessionRegistry;
    private final ApplicationEventPublisher events;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${app.verification.ttl-hours:24}")
    private long verifyTtlHours;

    @Value("${app.reset.ttl-minutes:60}")
    private long resetTtlMinutes;

    public AccountService(UserRepository userRepository, UserTokenRepository tokenRepository,
                          PasswordEncoder passwordEncoder, EmailService emailService,
                          RefreshTokenService refreshTokenService, SessionRegistry sessionRegistry,
                          ApplicationEventPublisher events) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.refreshTokenService = refreshTokenService;
        this.sessionRegistry = sessionRegistry;
        this.events = events;
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
        events.publishEvent(new AuditEvent(u.getId(), "PASSWORD_RESET", "USER", u.getId(), "Dat lai mat khau qua email"));
        return true;
    }

    // ---------------- Hồ sơ: đổi mật khẩu khi đang đăng nhập ----------------

    /**
     * Đổi mật khẩu cho user ĐANG đăng nhập.
     * - Nếu user đã có mật khẩu (passwordSet) thì phải nhập đúng mật khẩu hiện tại.
     * - Mật khẩu mới phải đủ mạnh và khác mật khẩu gần nhất.
     * - Sau khi đổi: giữ phiên hiện tại, đăng xuất các thiết bị KHÁC + thu hồi token API.
     */
    @Transactional
    public void changePassword(Long userId, String currentRaw, String newRaw, String currentSessionId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("NO_USER", "Không tìm thấy người dùng", HttpStatus.UNAUTHORIZED));
        if (u.isPasswordSet()) {
            if (currentRaw == null || !passwordEncoder.matches(currentRaw, u.getPasswordHash())) {
                throw new BusinessException("WRONG_PASSWORD", "Mật khẩu hiện tại không đúng", HttpStatus.BAD_REQUEST);
            }
        }
        if (!isStrong(newRaw)) {
            throw new BusinessException("WEAK_PASSWORD", PASSWORD_RULE_MSG, HttpStatus.BAD_REQUEST);
        }
        if (passwordEncoder.matches(newRaw, u.getPasswordHash())) {
            throw new BusinessException("PASSWORD_REUSED",
                    "Mật khẩu mới không được trùng với mật khẩu gần nhất", HttpStatus.BAD_REQUEST);
        }
        u.setPasswordHash(passwordEncoder.encode(newRaw));
        u.setPasswordSet(true);
        userRepository.save(u);

        expireOtherWebSessions(u.getEmail(), currentSessionId);
        refreshTokenService.revokeAllForUser(u.getId());
        refreshTokenService.invalidateAccessTokensBefore(u.getId());
        events.publishEvent(new AuditEvent(u.getId(), "PASSWORD_CHANGE", "USER", u.getId(), "Doi mat khau"));
    }

    // ---------------- Hồ sơ: quản lý thiết bị đăng nhập ----------------

    /** Danh sách phiên web đang hoạt động của user (đánh dấu phiên hiện tại). */
    public List<WebSessionInfo> listWebSessions(String email, String currentSessionId) {
        List<WebSessionInfo> out = new ArrayList<>();
        if (email == null) return out;
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            String username = principalName(principal);
            if (!email.equals(username)) continue;
            for (SessionInformation si : sessionRegistry.getAllSessions(principal, false)) {
                Instant last = si.getLastRequest() != null ? si.getLastRequest().toInstant() : null;
                out.add(new WebSessionInfo(si.getSessionId(), last, si.getSessionId().equals(currentSessionId)));
            }
        }
        out.sort(Comparator.comparing(WebSessionInfo::lastRequest, Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    /** Đăng xuất khỏi mọi thiết bị KHÁC (giữ phiên hiện tại) + thu hồi token API (mobile). Trả về số phiên web đã hủy. */
    @Transactional
    public int logoutOtherDevices(Long userId, String email, String currentSessionId) {
        int n = expireOtherWebSessions(email, currentSessionId);
        refreshTokenService.revokeAllForUser(userId);
        refreshTokenService.invalidateAccessTokensBefore(userId);
        return n;
    }

    // ---------------- Hồ sơ: xoá tài khoản (soft delete) ----------------

    /**
     * Khách tự xoá tài khoản: chuyển sang CLOSED + soft delete, giải phóng email,
     * đăng xuất mọi thiết bị + thu hồi token. Nếu user có mật khẩu thì phải xác nhận đúng.
     */
    @Transactional
    public void closeAccount(Long userId, String rawPassword) {
        User u = userRepository.findById(userId).orElse(null);
        if (u == null) return;
        if (u.isPasswordSet()) {
            if (rawPassword == null || !passwordEncoder.matches(rawPassword, u.getPasswordHash())) {
                throw new BusinessException("WRONG_PASSWORD", "Mật khẩu không đúng", HttpStatus.BAD_REQUEST);
            }
        }
        String oldEmail = u.getEmail();
        u.setStatus(UserStatus.CLOSED);
        u.setDeletedAt(Instant.now());
        u.setGoogleLinked(false);
        // Giải phóng email để có thể đăng ký lại + chặn đăng nhập bằng email cũ.
        String freed = "del_" + u.getId() + "_" + oldEmail;
        if (freed.length() > 150) freed = freed.substring(0, 150);
        u.setEmail(freed);
        userRepository.save(u);

        refreshTokenService.revokeAllForUser(u.getId());
        refreshTokenService.invalidateAccessTokensBefore(u.getId());
        expireWebSessions(oldEmail);
        events.publishEvent(new AuditEvent(u.getId(), "ACCOUNT_CLOSED", "USER", u.getId(), "Khach tu dong tai khoan"));
    }

    // ---------------- Hồ sơ: gửi lại email kích hoạt ----------------

    /** Gửi lại email kích hoạt nếu tài khoản còn INACTIVE. */
    @Transactional
    public void resendVerification(Long userId) {
        User u = userRepository.findById(userId).orElse(null);
        if (u == null || u.getStatus() != UserStatus.INACTIVE) return;
        String token = issue(u.getId(), TokenPurpose.VERIFY_EMAIL, Duration.ofHours(verifyTtlHours));
        emailService.sendVerification(u.getEmail(), baseUrl + "/verify?token=" + token, LocaleContextHolder.getLocale());
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
            String username = principalName(principal);
            if (email.equals(username)) {
                for (SessionInformation si : sessionRegistry.getAllSessions(principal, false)) {
                    si.expireNow();
                }
            }
        }
    }

    /** Hết hạn mọi phiên web của user TRỪ phiên hiện tại. Trả về số phiên đã hủy. */
    private int expireOtherWebSessions(String email, String currentSessionId) {
        int n = 0;
        if (email == null) return 0;
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            String username = principalName(principal);
            if (!email.equals(username)) continue;
            for (SessionInformation si : sessionRegistry.getAllSessions(principal, false)) {
                if (!si.getSessionId().equals(currentSessionId)) { si.expireNow(); n++; }
            }
        }
        return n;
    }

    /**
     * Tên định danh (email) của principal trong SessionRegistry.
     * Form-login -> UserDetails.getUsername(); đăng nhập Google -> OAuth2User.getName() (nameAttributeKey="email").
     */
    private static String principalName(Object principal) {
        if (principal instanceof UserDetails ud) return ud.getUsername();
        if (principal instanceof org.springframework.security.oauth2.core.user.OAuth2User ou) return ou.getName();
        if (principal instanceof java.security.Principal p) return p.getName();
        return String.valueOf(principal);
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
