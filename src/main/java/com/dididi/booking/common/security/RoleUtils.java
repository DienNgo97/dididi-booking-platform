package com.dididi.booking.common.security;

import com.dididi.booking.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

/** Tien ich kiem tra role tu Authentication (authority dang ROLE_&lt;role&gt;). */
public final class RoleUtils {

    private RoleUtils() {
    }

    public static boolean hasRole(Authentication auth, String role) {
        if (auth == null) {
            return false;
        }
        String authority = "ROLE_" + role;
        return auth.getAuthorities().stream().anyMatch(g -> authority.equals(g.getAuthority()));
    }

    public static boolean isSuperAdmin(Authentication auth) {
        return hasRole(auth, "SUPER_ADMIN");
    }

    public static void requireSuperAdmin(Authentication auth) {
        if (!isSuperAdmin(auth)) {
            throw new BusinessException("FORBIDDEN",
                    "Chỉ Super Admin được phép thực hiện thao tác này", HttpStatus.FORBIDDEN);
        }
    }
}
