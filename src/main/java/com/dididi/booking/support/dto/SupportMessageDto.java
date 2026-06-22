package com.dididi.booking.support.dto;

import com.dididi.booking.support.domain.SupportMessage;

import java.time.Instant;

/** 1 tin nhắn khi xem chi tiết hội thoại. */
public record SupportMessageDto(
        Long id,
        String role,
        String content,
        String source,
        boolean escalated,
        String bookingCode,
        Long userId,
        Instant createdAt
) {
    public static SupportMessageDto from(SupportMessage m) {
        return new SupportMessageDto(
                m.getId(),
                m.getRole() == null ? null : m.getRole().name(),
                m.getContent(),
                m.getSource(),
                m.isEscalated(),
                m.getBookingCode(),
                m.getUserId(),
                m.getCreatedAt());
    }
}
