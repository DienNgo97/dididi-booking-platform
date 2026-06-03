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
    public SecurityFilterChain webChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/", "/home", "/hotels/**", "/flights/**", "/trip-planner/**",
                                "/login", "/register",
                                "/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/account/**", "/booking/**", "/payment/**", "/checkout/**").authenticated()
                        .anyRequest().permitAll()
                )
                .formLogin(f -> f
                        .loginPage("/login")
                        .defaultSuccessUrl("/", false)
                        .permitAll())
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
