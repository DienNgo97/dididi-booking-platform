package com.dididi.booking.audit.api.dto;

import com.dididi.booking.audit.domain.entity.AuditLog;

import java.time.Instant;

public record AuditLogDto(
        Long id,
        Long actorUserId,
        String actorEmail,
        String action,
        String targetType,
        Long targetId,
        String detail,
        Instant createdAt) {

    public static AuditLogDto from(AuditLog a) {
        return new AuditLogDto(a.getId(), a.getActorUserId(), a.getActorEmail(), a.getAction(),
                a.getTargetType(), a.getTargetId(), a.getDetail(), a.getCreatedAt());
    }
}
