package com.dididi.booking.identity.service;

import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.identity.api.dto.LoginRequest;
import com.dididi.booking.identity.api.dto.LoginResponse;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.UserStatus;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.identity.security.LoginAuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAuditService loginAuditService;
    private final long accessTokenMinutes;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       LoginAuditService loginAuditService,
                       @Value("${app.jwt.access-token-minutes:60}") long accessTokenMinutes) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.loginAuditService = loginAuditService;
        this.accessTokenMinutes = accessTokenMinutes;
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

    /** Thu hồi refresh token (logout). */
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }
}
