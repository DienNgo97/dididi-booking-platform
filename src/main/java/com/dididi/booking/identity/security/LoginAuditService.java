package com.dididi.booking.identity.security;

import com.dididi.booking.audit.event.AuditEvent;
import com.dididi.booking.identity.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * Ghi nhat ky AUDIT cho moi lan DANG NHAP thanh cong (action = LOGIN).
 *
 * Bao het cac luong dang nhap cua he thong:
 *  - Form login (web Thymeleaf) + Google OAuth2: bat tu dong qua su kien
 *    {@link InteractiveAuthenticationSuccessEvent} ma Spring Security phat ra trong
 *    AbstractAuthenticationProcessingFilter sau khi xac thuc thanh cong. Voi ca hai
 *    luong nay, {@code authentication.getName()} chinh la email (form login dung
 *    username=email; OAuth2 dat nameAttributeKey=email).
 *  - Dang nhap REST/JWT (Angular, Flutter): AuthService goi {@link #record(Long, String)}.
 *  - Dang nhap OTP qua email (web): AuthWebController goi {@link #recordByEmail(String, String)}
 *    (luong nay set SecurityContext thu cong nen KHONG phat InteractiveAuthenticationSuccessEvent).
 *
 * Phat ra {@link AuditEvent} de tan dung listener ghi-bat-dong-bo san co -> khong lam cham dang nhap.
 */
@Service
public class LoginAuditService {

    private static final String ACTION = "LOGIN";

    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;

    public LoginAuditService(UserRepository userRepository, ApplicationEventPublisher events) {
        this.userRepository = userRepository;
        this.events = events;
    }

    /** Form login + Google OAuth2 (deu di qua security filter chain). */
    @EventListener
    public void onInteractiveLogin(InteractiveAuthenticationSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        if (auth == null) {
            return;
        }
        String method = (auth.getPrincipal() instanceof OAuth2User) ? "Google" : "mat khau";
        recordByEmail(auth.getName(), method);   // getName() = email (ca form login lan OAuth2)
    }

    /** Ghi audit theo email (tu tra ra userId). Bo qua neu khong tim thay user. */
    public void recordByEmail(String email, String method) {
        if (email == null || email.isBlank()) {
            return;
        }
        userRepository.findByEmail(email).ifPresent(u -> record(u.getId(), method));
    }

    /** Ghi audit theo userId da biet (dung cho dang nhap REST/JWT). */
    public void record(Long userId, String method) {
        if (userId == null) {
            return;
        }
        events.publishEvent(new AuditEvent(userId, ACTION, "USER", userId, "Dang nhap (" + method + ")"));
    }
}
