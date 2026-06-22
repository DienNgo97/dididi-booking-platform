package com.dididi.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * Chain 2: Web (Thymeleaf SSR) - session + custom form login (trang /login Thymeleaf).
 * Xac thuc qua CustomUserDetailsService (bang users) + BCrypt.
 */
@Configuration
public class SecurityWebConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain webChain(HttpSecurity http,
                                        SessionRegistry sessionRegistry,
                                        com.dididi.booking.identity.security.CustomOAuth2UserService oauth2UserService,
                                        com.dididi.booking.identity.security.OAuth2LoginSuccessHandler oauth2SuccessHandler) throws Exception {
        http
                // Tro ly CSKH la endpoint hoi-dap/log khong trang thai -> mien CSRF (bong bong chat o moi trang).
                .csrf(c -> c.ignoringRequestMatchers("/support/ask", "/support/log"))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/", "/home", "/hotels/**", "/flights/**", "/trip-planner/**",
                                "/login", "/login/**", "/register", "/vendor-register",
                                "/forgot-password", "/reset-password", "/verify",
                                "/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/payment/vnpay-return", "/payment/vnpay-ipn").permitAll()
                        .requestMatchers(HttpMethod.GET, "/g/**").permitAll()   // xem bang dieu khien nhom qua link moi
                        .requestMatchers("/account/**", "/booking/**", "/payment/**", "/checkout/**",
                                "/company-invite/**", "/groups/**", "/g/**").authenticated()
                        .anyRequest().permitAll()
                )
                .formLogin(f -> f
                        .loginPage("/login")
                        .defaultSuccessUrl("/", false)
                        .failureHandler(loginFailureHandler())
                        .permitAll())
                .oauth2Login(o -> o
                        .loginPage("/login")
                        .userInfoEndpoint(ui -> ui.userService(oauth2UserService))
                        .successHandler(oauth2SuccessHandler))
                .logout(l -> l.logoutUrl("/logout").logoutSuccessUrl("/"))
                .sessionManagement(s -> s
                        // Session het han (vd bi dang xuat moi thiet bi khi doi mat khau) -> ve trang chu,
                        // KHONG hien thong bao mac dinh "This session has been expired...".
                        .invalidSessionUrl("/")
                        .maximumSessions(3)
                        .sessionRegistry(sessionRegistry)
                        .expiredUrl("/"));
        return http.build();
    }

    /**
     * Dang nhap mat khau that bai: giu nguoi dung o BUOC MAT KHAU (mode=password).
     * Tai khoan chua kich hoat/da khoa -> kem co inactive.
     */
    private AuthenticationFailureHandler loginFailureHandler() {
        return (request, response, exception) -> {
            String target = "/login?mode=password&error";
            if (exception instanceof DisabledException || exception instanceof LockedException) {
                target = "/login?mode=password&inactive";
            }
            response.sendRedirect(request.getContextPath() + target);
        };
    }

    /** Theo doi phien dang nhap (web) de co the het han tat ca phien cua 1 user khi doi mat khau. */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Phat su kien vong doi HttpSession cho SessionRegistry cap nhat dung. */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    /** BCrypt strength 12 (System Design 7.3). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
