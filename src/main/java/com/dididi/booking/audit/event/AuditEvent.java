package com.dididi.booking.audit.event;

/**
 * Su kien can ghi audit. Phat ra TRONG giao dich cua hanh dong; listener se ghi
 * SAU KHI commit + BAT DONG BO -> khong ghi nham khi rollback, va khong lam cham API.
 */
public record AuditEvent(
        Long actorUserId,
        String action,
        String targetType,
        Long targetId,
        String detail) {
}
