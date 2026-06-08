package com.dididi.booking.vendor.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Vendor tu dang ky (public). Tao tai khoan + khach san o trang thai cho duyet. */
public record VendorRegisterRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        String fullName,
        String phone,
        @NotBlank String hotelName,
        String city,
        String address,
        Integer starRating) {
}
