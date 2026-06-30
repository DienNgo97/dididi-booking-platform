package com.dididi.booking.web;

import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Bổ sung dữ liệu dùng chung cho mọi trang SSR: tên đầy đủ của user đang đăng nhập,
 * để thanh nav chào bằng HỌ TÊN thay vì email. Chỉ áp dụng cho @Controller (trang Thymeleaf).
 */
@ControllerAdvice(annotations = Controller.class)
public class GlobalNavAdvice {

    private final UserRepository userRepository;

    /** API key Google Maps (web/browser key). Để trong application-local.yml, KHÔNG commit. */
    @Value("${app.maps.api-key:}")
    private String mapsApiKey;

    public GlobalNavAdvice(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Đưa API key ra mọi trang SSR để nhúng Google Maps. */
    @ModelAttribute("mapsApiKey")
    public String mapsApiKey() {
        return mapsApiKey;
    }

    @ModelAttribute("navFullName")
    public String navFullName(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return null;
        String name = auth.getName();
        if (name == null || "anonymousUser".equals(name)) return null;
        return userRepository.findByEmail(name).map(User::getFullName).orElse(null);
    }
}
