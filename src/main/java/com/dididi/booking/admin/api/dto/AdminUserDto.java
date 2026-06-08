package com.dididi.booking.admin.api.dto;

import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.Role;
import com.dididi.booking.identity.domain.enums.UserStatus;

import java.time.Instant;

/** View user cho màn hình admin (Phase 4b). KHÔNG bao giờ trả passwordHash. */
public record AdminUserDto(
        Long id,
        String email,
        String fullName,
        String phone,
        Role role,
        UserStatus status,
        Long vendorId,
        Instant createdAt) {

    public static AdminUserDto from(User u) {
        return new AdminUserDto(
                u.getId(), u.getEmail(), u.getFullName(), u.getPhone(),
                u.getRole(), u.getStatus(), u.getVendorId(), u.getCreatedAt());
    }
}
