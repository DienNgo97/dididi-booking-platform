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

    public AuthApiController(AuthService authService, AccountService accountService,
                            UserRepository userRepository) {
        this.authService = authService;
        this.accountService = accountService;
        this.userRepository = userRepository;
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

    @Operation(summary = "Đăng xuất - thu hồi refresh token")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.ok(null, "Đã đăng xuất");
    }

    @Operation(summary = "Đăng ký tài khoản CUSTOMER (tạo ở trạng thái chờ kích hoạt, gửi email kích hoạt)")
    @PostMapping("/register")
    public ApiResponse<UserDto> register(@Valid @RequestBody RegisterRequest request) {
        User u = accountService.registerCustomer(request.email(), request.password(), request.fullName());
        return ApiResponse.ok(toDto(u), "Đăng ký thành công. Vui lòng kiểm tra email để kích hoạt tài khoản.");
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
