package com.dididi.booking.common.exception;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public BusinessException(String code, String message) {
        this(code, message, HttpStatus.BAD_REQUEST);
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }

    /**
     * i18n TẠI CHỖ HIỂN THỊ (TC-B-29): message gốc tiếng Việt được viết cứng ở ~139 điểm ném.
     * Thay vì sửa từng điểm, override getMessage() để tra khoá "err.<CODE>" theo locale của
     * request hiện tại — MỌI nơi đang gọi ex.getMessage() (flash, JSON API, trang lỗi) tự
     * được dịch mà không đổi một dòng call-site nào.
     *  - locale vi: trả nguyên văn chuỗi gốc (chính xác từng ngữ cảnh).
     *  - locale khác: bản dịch theo CODE (một bản dịch/CODE — chấp nhận bớt đặc thù ngữ cảnh).
     *  - thiếu khoá / ngoài request (scheduler, log): trả chuỗi gốc.
     */
    @Override
    public String getMessage() {
        return com.dididi.booking.common.i18n.I18nSupport.msg("err." + code, super.getMessage());
    }

    /** Chuỗi gốc tiếng Việt như lúc ném — dùng khi cần ghi log/audit ổn định không phụ thuộc locale. */
    public String rawMessage() { return super.getMessage(); }
}
