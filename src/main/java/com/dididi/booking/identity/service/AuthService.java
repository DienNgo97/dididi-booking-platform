package com.dididi.booking.identity.service;

import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.identity.api.dto.LoginRequest;
import com.dididi.booking.identity.api.dto.LoginResponse;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.UserStatus;
import com.dididi.booking.identity.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long accessTokenMinutes;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       @Value("${app.jwt.access-token-minutes:60}") long accessTokenMinutes) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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
        return new LoginResponse(token, "Bearer", accessTokenMinutes,
                user.getEmail(), user.getRole().name());
    }
}
