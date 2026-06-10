package com.dididi.booking.identity.security;

import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.Role;
import com.dididi.booking.identity.domain.enums.UserStatus;
import com.dididi.booking.identity.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Doi thong tin Google -> User noi bo. Tim theo email, neu chua co thi tao moi (CUSTOMER).
 * Tra ve OAuth2User co name = email (nameAttributeKey="email") de CurrentUser.require(auth) resolve dung,
 * va quyen "ROLE_" + role cua user.
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public CustomOAuth2UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauthUser = super.loadUser(userRequest);
        Map<String, Object> attrs = oauthUser.getAttributes();
        String email = (String) attrs.get("email");
        String name = (String) attrs.get("name");
        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("email_unavailable"),
                    "Tài khoản Google không cung cấp email");
        }

        User user = userRepository.findByEmail(email).orElse(null);
        boolean isNew = (user == null);
        if (isNew) {
            User u = new User();
            u.setEmail(email);
            u.setFullName(name != null ? name : email);
            // Mat khau ngau nhien khong dung de dang nhap form (cot password_hash NOT NULL).
            u.setPasswordHash(passwordEncoder.encode("OAUTH2_" + UUID.randomUUID()));
            u.setRole(Role.CUSTOMER);
            u.setStatus(UserStatus.ACTIVE);
            user = userRepository.save(u);
        }

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new OAuth2AuthenticationException(new OAuth2Error("account_locked"), "Tài khoản đã bị khoá");
        }

        // Copy attributes + danh dau tai khoan vua tao (de success handler hien thong bao dang ky).
        Map<String, Object> merged = new java.util.HashMap<>(attrs);
        merged.put("dididi_new_user", isNew);

        String authority = "ROLE_" + user.getRole().name();
        return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority(authority)), merged, "email");
    }
}
