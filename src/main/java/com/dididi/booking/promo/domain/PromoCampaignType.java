package com.dididi.booking.promo.domain;

/**
 * Các chương trình khuyến mãi CÁ NHÂN HOÁ (mỗi loại 1 bản ghi cấu hình trong bảng promo_campaign).
 * Tên enum cũng là "mã chương trình" hiển thị cho admin.
 */
public enum PromoCampaignType {

    /** Quà sinh nhật: tặng vào đúng ngày sinh (mỗi năm 1 lần). */
    BIRTHDAY("Quà sinh nhật", "BD"),

    /** Kéo khách quay lại: lâu không đặt đơn nào (theo số ngày cấu hình). */
    WIN_BACK("Ưu đãi khách quay lại", "WB"),

    /** Tri ân theo hạng thành viên (Gold trở lên), mỗi chu kỳ 1 lần. */
    TIER_REWARD("Tri ân hạng thành viên", "TIER"),

    /** Chào mừng khách mới đăng ký, dùng cho đơn đầu tiên. */
    WELCOME("Chào mừng khách mới", "WEL");

    private final String viName;
    /** Tiền tố mã voucher sinh ra (vd BD-A1B2C3). */
    private final String codePrefix;

    PromoCampaignType(String viName, String codePrefix) {
        this.viName = viName;
        this.codePrefix = codePrefix;
    }

    public String getViName() { return viName; }
    public String getCodePrefix() { return codePrefix; }
}
