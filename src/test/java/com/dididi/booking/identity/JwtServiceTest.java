package com.dididi.booking.identity;

import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.Role;
import com.dididi.booking.identity.service.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            "test-secret-key-that-is-long-enough-32bytes-minimum!!",
            60,
            "dididi-booking-platform");

    @Test
    void generateAndValidateToken() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@dididi.local");
        user.setRole(Role.CUSTOMER);

        String token = jwtService.generateToken(user);
        assertTrue(jwtService.validate(token));

        Claims claims = jwtService.parse(token);
        assertEquals("1", claims.getSubject());
        assertEquals("test@dididi.local", claims.get("email", String.class));
        assertEquals("CUSTOMER", claims.get("role", String.class));
    }

    @Test
    void invalidTokenFailsValidation() {
        assertFalse(jwtService.validate("not.a.valid.token"));
    }
}
