package com.dididi.booking.support.dto;

/**
 * Kết quả 1 lượt hỏi trợ lý CSKH.
 *  - source: "kb"  = trả lời từ tri thức nội bộ
 *            "llm" = trả lời từ mô hình ngôn ngữ (nếu cấu hình api-key)
 *            "none"= không trả lời được -> nên chuyển tổng đài viên
 *  - escalate: true = gợi ý nút "Gặp tổng đài viên".
 */
public record SupportAnswer(String answer, String source, boolean escalate) {

    public static SupportAnswer kb(String answer) {
        return new SupportAnswer(answer, "kb", false);
    }

    public static SupportAnswer kbEscalate(String answer) {
        return new SupportAnswer(answer, "kb", true);
    }

    public static SupportAnswer llm(String answer) {
        return new SupportAnswer(answer, "llm", false);
    }

    public static SupportAnswer none(String answer) {
        return new SupportAnswer(answer, "none", true);
    }
}
