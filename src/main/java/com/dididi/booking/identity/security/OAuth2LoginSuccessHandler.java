package com.dididi.booking.identity.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;

import java.io.IOException;

/**
 * Sau khi dang nhap Google thanh cong: neu tai khoan vua duoc tao (dang ky), hien thong bao chao mung
 * (qua FlashMap -> bien ${message} o trang dich). Van giu hanh vi quay lai trang truoc do (saved request)
 * hoac ve trang chu.
 */
@Component
public class OAuth2LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    public OAuth2LoginSuccessHandler() {
        setDefaultTargetUrl("/");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (authentication.getPrincipal() instanceof OAuth2User ou
                && Boolean.TRUE.equals(ou.getAttribute("dididi_new_user"))) {
            FlashMap flashMap = new FlashMap();
            flashMap.put("message", "Tạo tài khoản thành công bằng Google. Chào mừng bạn đến với Dididi!");
            new SessionFlashMapManager().saveOutputFlashMap(flashMap, request, response);
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
