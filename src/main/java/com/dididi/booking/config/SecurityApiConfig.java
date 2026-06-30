package com.dididi.booking.config;

import com.dididi.booking.identity.web.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Chain 1: REST API - stateless, JWT, CORS cho Angular (4200) + Flutter.
 */
@Configuration
@EnableWebSecurity
public class SecurityApiConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    public SecurityApiConfig(JwtAuthenticationFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/**")
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/hotels/**", "/api/v1/flights/**", "/api/v1/master/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/reviews/**").permitAll()
                        // media MXH: KHONG permitAll nua — yeu cau dang nhap + kiem tra canView trong controller
                        .requestMatchers(HttpMethod.POST, "/api/v1/trip-planner/**").permitAll()
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/vendor/**").hasAnyRole("VENDOR", "ADMIN", "SUPER_ADMIN")
                        .anyRequest().authenticated()
                );
        return http.build();
    }

    /**
     * SEC-06: CORS origins externalize qua app.cors.allowed-origins (CSV), mac dinh http://localhost:4200.
     * Prod set bien moi truong / yaml cho domain that thay vi sua code. allowCredentials=true nen KHONG
     * dung "*" — luon liet ke origin cu the.
     */
    @Value("${app.cors.allowed-origins:http://localhost:4200}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(allowedOrigins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/api/**", cfg);
        return src;
    }
}
