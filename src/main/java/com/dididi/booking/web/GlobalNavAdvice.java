package com.dididi.booking.web;

import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.social.service.SocialProfileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Optional;

/**
 * Bổ sung dữ liệu dùng chung cho mọi trang SSR: tên đầy đủ của user đang đăng nhập,
 * để thanh nav chào bằng HỌ TÊN thay vì email. Chỉ áp dụng cho @Controller (trang Thymeleaf).
 */
@ControllerAdvice(annotations = Controller.class)
public class GlobalNavAdvice {

    private final UserRepository userRepository;
    private final SocialProfileService socialProfileService;

    /** API key Google Maps (web/browser key). Để trong application-local.yml, KHÔNG commit. */
    @Value("${app.maps.api-key:}")
    private String mapsApiKey;

    public GlobalNavAdvice(UserRepository userRepository, SocialProfileService socialProfileService) {
        this.userRepository = userRepository;
        this.socialProfileService = socialProfileService;
    }

    /** Đưa API key ra mọi trang SSR để nhúng Google Maps. */
    @ModelAttribute("mapsApiKey")
    public String mapsApiKey() {
        return mapsApiKey;
    }

    @ModelAttribute("navFullName")
    public String navFullName(Authentication auth) {
        return currentUser(auth).map(User::getFullName).orElse(null);
    }

    /**
     * Ảnh đại diện trên thanh nav — cùng một ảnh với hồ sơ Cộng đồng (social_profiles.avatar_key).
     * Null thì nav vẫn vẽ chữ cái đầu như cũ.
     */
    @ModelAttribute("navAvatarUrl")
    public String navAvatarUrl(Authentication auth) {
        return currentUser(auth)
                .flatMap(u -> socialProfileService.findByUserId(u.getId())
                        .map(p -> SocialProfileService.avatarUrl(u.getId(), p.getAvatarKey())))
                .orElse(null);
    }

    private Optional<User> currentUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        String name = auth.getName();
        if (name == null || "anonymousUser".equals(name)) {
            return Optional.empty();
        }
        return userRepository.findByEmail(name);
    }
}
