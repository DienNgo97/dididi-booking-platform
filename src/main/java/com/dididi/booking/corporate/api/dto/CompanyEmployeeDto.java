package com.dididi.booking.corporate.api.dto;

import com.dididi.booking.identity.domain.entity.User;

public record CompanyEmployeeDto(Long userId, String email, String fullName, String role) {
    public static CompanyEmployeeDto from(User u) {
        return new CompanyEmployeeDto(u.getId(), u.getEmail(), u.getFullName(),
                u.getRole() == null ? null : u.getRole().name());
    }
}
