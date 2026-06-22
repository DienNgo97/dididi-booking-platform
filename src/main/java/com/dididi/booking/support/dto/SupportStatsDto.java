package com.dididi.booking.support.dto;

/** Số liệu tổng quan cho trang admin. */
public record SupportStatsDto(
        long totalMessages,
        long totalQuestions,        // số tin role=USER
        long totalConversations,
        long escalatedConversations,
        double escalationRate,      // % hội thoại có chuyển tổng đài
        long kbAnswers,
        long llmAnswers,
        long unresolvedAnswers,     // source=none
        long agentMessages
) {}
