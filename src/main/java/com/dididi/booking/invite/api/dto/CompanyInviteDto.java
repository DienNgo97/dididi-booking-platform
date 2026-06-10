package com.dididi.booking.invite.api.dto;

import com.dididi.booking.invite.domain.CompanyInvite;

import java.time.Instant;

public record CompanyInviteDto(
        Long id, Long companyId, String email, String token,
        String status, Instant expiresAt, String acceptUrl) {

    public static CompanyInviteDto from(CompanyInvite i, String baseUrl) {
        return new CompanyInviteDto(i.getId(), i.getCompanyId(), i.getEmail(), i.getToken(),
                i.getStatus().name(), i.getExpiresAt(), baseUrl + "/company-invite/" + i.getToken());
    }
}
