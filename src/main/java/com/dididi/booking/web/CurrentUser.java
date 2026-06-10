package com.dididi.booking.web;

import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    private final UserRepository userRepository;

    public CurrentUser(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User require(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new BusinessException("NO_USER", "Không tìm thấy người dùng", HttpStatus.UNAUTHORIZED));
    }

    public Long id(Authentication auth) {
        return require(auth).getId();
    }

    /** Tra ve userId neu da dang nhap (khong phai anonymous), nguoc lai null - dung cho trang public. */
    public Long idOrNull(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return null;
        String name = auth.getName();
        if (name == null || "anonymousUser".equals(name)) return null;
        return userRepository.findByEmail(name).map(User::getId).orElse(null);
    }
}
