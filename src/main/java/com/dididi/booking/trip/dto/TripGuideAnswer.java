package com.dididi.booking.trip.dto;

import java.util.List;

/**
 * Kết quả 1 lượt hỏi AI hướng dẫn viên du lịch (Trip Guide).
 *  - source  : "kb" = trả lời từ tri thức nội bộ; "llm" = từ mô hình ngôn ngữ (khi cấu hình api-key).
 *  - suggests: các câu hỏi gợi ý tiếp theo (client hiển thị thành nút bấm nhanh).
 */
public record TripGuideAnswer(String answer, String source, List<String> suggests) {
}
