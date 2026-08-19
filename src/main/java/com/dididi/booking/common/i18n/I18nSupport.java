package com.dididi.booking.common.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Cầu nối i18n cho các CHUỖI SINH TỪ JAVA (BusinessException, flash message) — TC-B-29 mở rộng.
 *
 * VÌ SAO CẦN: toàn bộ message lỗi/flash được viết cứng tiếng Việt tại ~190 điểm ném/flash rải khắp
 * controller + service. Sửa từng điểm để nhận MessageSource là đập đi xây lại. Thay vào đó:
 *  - Chuỗi tiếng Việt gốc GIỮ NGUYÊN tại chỗ (locale vi dùng thẳng, không tra cứu — chính xác 100%).
 *  - Locale khác (en/zh): tra khoá trong messages_*.properties qua LocaleContextHolder (Spring đã set
 *    theo từng request). Không có khoá -> trả chuỗi gốc tiếng Việt (degrade an toàn, không vỡ trang).
 *
 * Static holder được nạp bởi Spring lúc khởi động ({@link #I18nSupport(MessageSource)}). Trước khi
 * context sẵn sàng (hoặc trong unit test thuần) mọi lời gọi đều rơi về fallback — không NPE.
 */
@Component
public class I18nSupport {

    private static volatile MessageSource source;

    public I18nSupport(MessageSource messageSource) {
        source = messageSource;
    }

    /** Tra khoá theo locale hiện tại; locale vi hoặc thiếu khoá -> trả fallback (chuỗi VN gốc). */
    public static String msg(String key, String fallbackVi, Object... args) {
        MessageSource s = source;
        Locale loc = LocaleContextHolder.getLocale();
        if (s == null || loc == null || "vi".equals(loc.getLanguage())) {
            return fallbackVi;
        }
        try {
            return s.getMessage(key, args, fallbackVi, loc);
        } catch (Exception e) {
            return fallbackVi;
        }
    }
}
