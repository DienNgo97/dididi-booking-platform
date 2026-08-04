package com.dididi.booking.identity.service;

import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.identity.api.dto.LoginRequest;
import com.dididi.booking.identity.api.dto.LoginResponse;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.Role;
import com.dididi.booking.identity.domain.enums.UserStatus;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.identity.security.LoginAuditService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAuditService loginAuditService;
    private final long accessTokenMinutes;
    /**
     * (Tuỳ chọn) (các) OAuth Client ID hợp lệ của Google, phân tách bằng dấu phẩy. Nếu để trống thì
     * chỉ kiểm tra chữ ký + email_verified qua endpoint tokeninfo mà không ràng buộc "aud".
     * Khi cấu hình, "aud" trong ID token phải khớp một trong các giá trị này (chống token của app khác).
     */
    private final String googleClientIds;
    private final OtpLoginService otpLoginService;
    private static final HttpClient GOOGLE_HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper GOOGLE_JSON = new ObjectMapper();

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       LoginAuditService loginAuditService,
                       OtpLoginService otpLoginService,
                       @Value("${app.jwt.access-token-minutes:60}") long accessTokenMinutes,
                       @Value("${app.google.client-ids:}") String googleClientIds) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.loginAuditService = loginAuditService;
        this.otpLoginService = otpLoginService;
        this.accessTokenMinutes = accessTokenMinutes;
        this.googleClientIds = googleClientIds;
    }

    /** Gửi OTP đăng nhập tới email (kết quả: SENT/INACTIVE/NOT_FOUND — controller không lộ ra ngoài). */
    public OtpLoginService.RequestResult requestOtp(String email) {
        return otpLoginService.request(email);
    }

    /** Đăng nhập bằng OTP email: xác thực mã rồi cấp JWT (giống login thường nhưng không cần mật khẩu). */
    public LoginResponse loginWithOtp(String email, String code) {
        if (!otpLoginService.verify(email, code)) {
            throw new BusinessException("BAD_OTP", "Mã OTP không đúng hoặc đã hết hạn", HttpStatus.UNAUTHORIZED);
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("BAD_OTP", "Mã OTP không hợp lệ", HttpStatus.UNAUTHORIZED));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("ACCOUNT_INACTIVE", "Account is not active", HttpStatus.FORBIDDEN);
        }
        String token = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.issue(user.getId());
        loginAuditService.record(user.getId(), "API-OTP");
        return new LoginResponse(token, refreshToken, "Bearer", accessTokenMinutes,
                user.getEmail(), user.getRole().name());
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException("BAD_CREDENTIALS",
                        "Invalid email or password", HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException("BAD_CREDENTIALS",
                    "Invalid email or password", HttpStatus.UNAUTHORIZED);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("ACCOUNT_INACTIVE",
                    "Account is not active", HttpStatus.FORBIDDEN);
        }

        String token = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.issue(user.getId());
        loginAuditService.record(user.getId(), "API");   // ghi nhat ky dang nhap (REST/JWT - Angular/Flutter)
        return new LoginResponse(token, refreshToken, "Bearer", accessTokenMinutes,
                user.getEmail(), user.getRole().name());
    }

    /** Cấp access token mới từ refresh token (xoay vòng refresh token). */
    public LoginResponse refresh(String refreshToken) {
        Long userId = refreshTokenService.userIdOf(refreshToken);
        if (userId == null) {
            throw new BusinessException("INVALID_REFRESH",
                    "Refresh token không hợp lệ hoặc đã hết hạn", HttpStatus.UNAUTHORIZED);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("INVALID_REFRESH",
                        "Refresh token không hợp lệ", HttpStatus.UNAUTHORIZED));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("ACCOUNT_INACTIVE", "Account is not active", HttpStatus.FORBIDDEN);
        }
        String newAccess = jwtService.generateToken(user);
        String newRefresh = refreshTokenService.rotate(userId, refreshToken);
        return new LoginResponse(newAccess, newRefresh, "Bearer", accessTokenMinutes,
                user.getEmail(), user.getRole().name());
    }

    /**
     * Thu hồi refresh token (logout) VÀ vô hiệu hoá mọi access token (JWT) đã cấp trước thời điểm này
     * cho cùng user (SEC-04). userId được xác định từ bearer token (nếu có), ngược lại suy từ refresh token.
     */
    public void logout(String refreshToken, Long bearerUserId) {
        Long userId = bearerUserId != null ? bearerUserId : refreshTokenService.userIdOf(refreshToken);
        refreshTokenService.revoke(refreshToken);
        if (userId != null) {
            refreshTokenService.invalidateAccessTokensBefore(userId);
        }
    }

    /**
     * Đăng nhập bằng Google (mobile/Flutter): xác thực ID token Google, tìm-hoặc-tạo user (CUSTOMER),
     * rồi cấp JWT + refresh token như đăng nhập thường. Cùng cơ chế "tìm theo email, chưa có thì tạo"
     * với luồng OAuth2 trên web (CustomOAuth2UserService).
     */
    public LoginResponse loginWithGoogle(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new BusinessException("BAD_GOOGLE_TOKEN", "Thiếu Google ID token", HttpStatus.BAD_REQUEST);
        }
        JsonNode info = verifyGoogleIdToken(idToken);

        String email = text(info, "email");
        boolean emailVerified = "true".equalsIgnoreCase(text(info, "email_verified"));
        String name = text(info, "name");
        if (email == null || email.isBlank() || !emailVerified) {
            throw new BusinessException("GOOGLE_EMAIL_UNVERIFIED",
                    "Tài khoản Google chưa xác thực email", HttpStatus.UNAUTHORIZED);
        }
        // Ràng buộc "aud" khớp OAuth Client ID của app (nếu đã cấu hình app.google.client-ids).
        if (googleClientIds != null && !googleClientIds.isBlank()) {
            String aud = text(info, "aud");
            boolean match = false;
            for (String cid : googleClientIds.split(",")) {
                if (cid.trim().equals(aud)) { match = true; break; }
            }
            if (!match) {
                throw new BusinessException("GOOGLE_AUD_MISMATCH",
                        "Google client ID không khớp", HttpStatus.UNAUTHORIZED);
            }
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            User u = new User();
            u.setEmail(email);
            u.setFullName(name != null ? name : email);
            // Mật khẩu ngẫu nhiên (cột password_hash NOT NULL); tài khoản này đăng nhập qua Google.
            u.setPasswordHash(passwordEncoder.encode("OAUTH2_" + UUID.randomUUID()));
            u.setRole(Role.CUSTOMER);
            u.setStatus(UserStatus.ACTIVE);
            u.setGoogleLinked(true);
            u.setPasswordSet(false);
            user = userRepository.save(u);
        } else {
            if (user.getStatus() != UserStatus.ACTIVE) {
                throw new BusinessException("ACCOUNT_INACTIVE", "Account is not active", HttpStatus.FORBIDDEN);
            }
            if (!user.isGoogleLinked()) {
                user.setGoogleLinked(true);
                user = userRepository.save(user);
            }
        }

        String token = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.issue(user.getId());
        loginAuditService.record(user.getId(), "API-GOOGLE");
        return new LoginResponse(token, refreshToken, "Bearer", accessTokenMinutes,
                user.getEmail(), user.getRole().name());
    }

    /** Gọi endpoint tokeninfo của Google để xác thực chữ ký + hạn của ID token. */
    private JsonNode verifyGoogleIdToken(String idToken) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://oauth2.googleapis.com/tokeninfo?id_token="
                            + URLEncoder.encode(idToken, StandardCharsets.UTF_8)))
                    .GET().build();
            HttpResponse<String> resp = GOOGLE_HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new BusinessException("BAD_GOOGLE_TOKEN",
                        "Google ID token không hợp lệ hoặc đã hết hạn", HttpStatus.UNAUTHORIZED);
            }
            return GOOGLE_JSON.readTree(resp.body());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("GOOGLE_VERIFY_FAILED",
                    "Không xác thực được Google token", HttpStatus.UNAUTHORIZED);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }
}
