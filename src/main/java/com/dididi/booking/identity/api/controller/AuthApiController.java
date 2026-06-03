package com.dididi.booking.identity.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.identity.api.dto.LoginRequest;
import com.dididi.booking.identity.api.dto.LoginResponse;
import com.dididi.booking.identity.api.dto.RegisterRequest;
import com.dididi.booking.identity.api.dto.UserDto;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.Role;
import com.dididi.booking.identity.domain.enums.UserStatus;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "Đăng ký / đăng nhập / thông tin tài khoản")
@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthApiController(AuthService authService, UserRepository userRepository,
                            PasswordEncoder passwordEncoder) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Operation(summary = "Đăng nhập, trả JWT access token")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @Operation(summary = "Đăng ký tài khoản CUSTOMER")
    @PostMapping("/register")
    public ApiResponse<UserDto> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("EMAIL_EXISTS", "Email đã được đăng ký", HttpStatus.CONFLICT);
        }
        User u = new User();
        u.setEmail(request.email());
        u.setPasswordHash(passwordEncoder.encode(request.password()));
        u.setFullName(request.fullName());
        u.setRole(Role.CUSTOMER);
        u.setStatus(UserStatus.ACTIVE);
        userRepository.save(u);
        return ApiResponse.ok(toDto(u), "Đăng ký thành công");
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
