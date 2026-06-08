package com.dididi.booking.admin.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Admin tạo tài khoản vendor + khách sạn DIRECT. */
public record CreateVendorRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        String fullName,
        String phone,
        @NotBlank String hotelName,
        String city,
        String address,
        Integer starRating) {
}
