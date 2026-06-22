package com.dididi.booking.support.dto;

import java.time.Instant;

/** 1 dòng tóm tắt hội thoại trong danh sách admin. */
public record ConversationSummaryDto(
        String conversationId,
        long messageCount,
        Instant startedAt,
        Instant lastAt,
        boolean escalated,
        Long userId
) {}
