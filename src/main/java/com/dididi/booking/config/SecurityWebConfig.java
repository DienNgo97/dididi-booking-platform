package com.dididi.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Chain 2: Web (Thymeleaf SSR) - session + custom form login (trang /login Thymeleaf).
 * Xac thuc qua CustomUserDetailsService (bang users) + BCrypt.
 */
@Configuration
public class SecurityWebConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain webChain(HttpSecurity http,
                                        com.dididi.booking.identity.security.CustomOAuth2UserService oauth2UserService,
                                        com.dididi.booking.identity.security.OAuth2LoginSuccessHandler oauth2SuccessHandler) throws Exception {
        http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/", "/home", "/hotels/**", "/flights/**", "/trip-planner/**",
                                "/login", "/register", "/vendor-register",
                                "/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/payment/vnpay-return", "/payment/vnpay-ipn").permitAll()
                        .requestMatchers("/account/**", "/booking/**", "/payment/**", "/checkout/**",
                                "/company-invite/**").authenticated()
                        .anyRequest().permitAll()
                )
                .formLogin(f -> f
                        .loginPage("/login")
                        .defaultSuccessUrl("/", false)
                        .permitAll())
                .oauth2Login(o -> o
                        .loginPage("/login")
                        .userInfoEndpoint(ui -> ui.userService(oauth2UserService))
                        .successHandler(oauth2SuccessHandler))
                .logout(l -> l.logoutUrl("/logout").logoutSuccessUrl("/"))
                .sessionManagement(s -> s.maximumSessions(3));
        return http.build();
    }

    /** BCrypt strength 12 (System Design 7.3). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
