package com.dididi.booking.admin.api.dto;

import com.dididi.booking.identity.domain.enums.Role;

public record CreateAdminRequest(String email, String fullName, String password, Role role) {}
