package com.dididi.booking.support.domain;

/** Vai trò của 1 tin nhắn trong hội thoại hỗ trợ. */
public enum SupportRole {
    USER,    // khách hỏi
    BOT,     // trợ lý AI trả lời (KB/LLM)
    AGENT,   // tổng đài viên (mô phỏng)
    SYSTEM   // sự kiện hệ thống (vd: chuyển tổng đài)
}
