package com.dididi.booking.flight.domain;

import java.util.Map;

/**
 * Tên hãng bay theo mã IATA hai ký tự.
 *
 * VÌ SAO CÓ FILE NÀY: template flights/list.html trước đây in cứng chuỗi "Vietnam Airlines"
 * dưới MỌI số hiệu chuyến bay — chuyến VJ997 của Vietjet và QH312 của Bamboo cũng hiện là
 * Vietnam Airlines. Đây là chuỗi mẫu lúc dựng giao diện, quên thay bằng dữ liệu thật.
 * Lỗi thuộc loại nguy hiểm âm thầm: trang vẫn chạy, không có exception, nhưng KHÁCH ĐỌC SAI
 * tên hãng mà mình đang bán vé cho họ.
 *
 * Tên hãng là danh từ riêng nên KHÔNG đưa vào i18n — giữ nguyên ở mọi ngôn ngữ.
 * Mã lạ (dữ liệu mới từ flight-provider) thì trả lại chính mã đó, thà hiện "XX" còn hơn
 * hiện sai tên một hãng khác.
 */
public final class Airlines {

    private static final Map<String, String> NAMES = Map.of(
            "VN", "Vietnam Airlines",
            "VJ", "Vietjet Air",
            "QH", "Bamboo Airways",
            "BL", "Pacific Airlines",
            "VU", "Vietravel Airlines"
    );

    private Airlines() { }

    /** Tên hãng theo mã; mã null/rỗng/không biết -> trả về chính mã (hoặc chuỗi rỗng). */
    public static String nameOf(String code) {
        if (code == null || code.isBlank()) return "";
        String key = code.trim().toUpperCase();
        return NAMES.getOrDefault(key, key);
    }
}
