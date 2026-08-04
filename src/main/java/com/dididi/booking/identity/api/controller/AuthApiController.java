package com.dididi.booking.identity.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.identity.api.dto.LoginRequest;
import com.dididi.booking.identity.api.dto.LoginResponse;
import com.dididi.booking.identity.api.dto.RefreshRequest;
import com.dididi.booking.identity.api.dto.RegisterRequest;
import com.dididi.booking.identity.api.dto.UserDto;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.identity.service.AccountService;
import com.dididi.booking.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "Đăng ký / đăng nhập / thông tin tài khoản")
@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private final AuthService authService;
    private final AccountService accountService;
    private final UserRepository userRepository;
    private final com.dididi.booking.identity.service.RefreshTokenService refreshTokenService;

    public AuthApiController(AuthService authService, AccountService accountService,
                            UserRepository userRepository,
                            com.dididi.booking.identity.service.RefreshTokenService refreshTokenService) {
        this.authService = authService;
        this.accountService = accountService;
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
    }

    @Operation(summary = "Đăng nhập, trả JWT access token")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @Operation(summary = "Cấp access token mới bằng refresh token (xoay vòng refresh token)")
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()), "Token refreshed");
    }

    @Operation(summary = "Đăng nhập bằng Google (mobile): xác thực ID token, tìm-hoặc-tạo user, trả JWT")
    @PostMapping("/google")
    public ApiResponse<LoginResponse> google(@RequestBody java.util.Map<String, String> body) {
        return ApiResponse.ok(authService.loginWithGoogle(body.getOrDefault("idToken", "")));
    }

    @Operation(summary = "Gửi mã OTP đăng nhập qua email (luôn trả OK để không lộ email tồn tại)")
    @PostMapping("/otp/request")
    public ApiResponse<Void> requestOtp(@RequestBody java.util.Map<String, String> body) {
        authService.requestOtp(body.getOrDefault("email", ""));
        return ApiResponse.ok(null, "Nếu email hợp lệ, mã OTP đã được gửi.");
    }

    @Operation(summary = "Đăng nhập bằng OTP email: xác thực mã rồi trả JWT")
    @PostMapping("/otp/verify")
    public ApiResponse<LoginResponse> verifyOtp(@RequestBody java.util.Map<String, String> body) {
        return ApiResponse.ok(authService.loginWithOtp(body.getOrDefault("email", ""), body.getOrDefault("code", "")));
    }

    @Operation(summary = "Đăng xuất - thu hồi refresh token + vô hiệu access token đã cấp")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest request, Authentication authentication) {
        Long bearerUserId = null;
        if (authentication != null && authentication.getName() != null) {
            try {
                bearerUserId = Long.valueOf(authentication.getName()); // principal = userId (JwtAuthenticationFilter)
            } catch (NumberFormatException ignore) { /* khong co bearer hop le -> dua vao refresh token */ }
        }
        authService.logout(request.refreshToken(), bearerUserId);
        return ApiResponse.ok(null, "Đã đăng xuất");
    }

    @Operation(summary = "Đăng xuất khỏi TẤT CẢ thiết bị (thu hồi mọi refresh token của user)")
    @PostMapping("/logout-all")
    public ApiResponse<Void> logoutAll(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        refreshTokenService.revokeAllForUser(Long.valueOf(authentication.getName()));
        return ApiResponse.ok(null, "Đã đăng xuất khỏi tất cả thiết bị");
    }

    @Operation(summary = "Đăng ký tài khoản CUSTOMER (tạo ở trạng thái chờ kích hoạt, gửi email kích hoạt)")
    @PostMapping("/register")
    public ApiResponse<UserDto> register(@Valid @RequestBody RegisterRequest request) {
        User u = accountService.registerCustomer(request.email(), request.password(), request.fullName());
        return ApiResponse.ok(toDto(u), "Đăng ký thành công. Vui lòng kiểm tra email để kích hoạt tài khoản.");
    }

    @Operation(summary = "Quên mật khẩu: gửi email chứa liên kết/token đặt lại (luôn trả OK để không lộ email tồn tại)")
    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@RequestBody java.util.Map<String, String> body) {
        accountService.requestPasswordReset(body.getOrDefault("email", ""));
        return ApiResponse.ok(null, "Nếu email tồn tại, chúng tôi đã gửi hướng dẫn đặt lại mật khẩu.");
    }

    @Operation(summary = "Đặt lại mật khẩu bằng token nhận qua email")
    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@RequestBody java.util.Map<String, String> body) {
        boolean ok = accountService.resetPassword(body.getOrDefault("token", ""), body.getOrDefault("newPassword", ""));
        if (!ok) {
            throw new BusinessException("RESET_FAILED", "Token không hợp lệ hoặc đã hết hạn", HttpStatus.BAD_REQUEST);
        }
        return ApiResponse.ok(null, "Đặt lại mật khẩu thành công. Vui lòng đăng nhập.");
    }

    @Operation(summary = "Thông tin tài khoản đang đăng nhập (cần Bearer token)")
    @GetMapping("/me")
    public ApiResponse<UserDto> me(Authentication authentication) {
        if (authentication == null) {
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        Long userId = Long.valueOf(authentication.getName()); // principal = userId (set boi JwtAuthenticationFilter)
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        return ApiResponse.ok(toDto(u));
    }

    private UserDto toDto(User u) {
        return new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getRole().name());
    }
}
