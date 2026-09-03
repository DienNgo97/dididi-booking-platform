package com.dididi.booking.social.domain.enums;

/** Loai tin nhan: van ban, anh, chia se 1 bai dang, hoac thong bao he thong cua nhom. */
public enum MessageType {
    TEXT,
    IMAGE,
    POST_SHARE,
    /** Dòng thông báo do hệ thống chèn: ai tạo nhóm, ai được thêm, ai rời nhóm, đổi tên nhóm. */
    SYSTEM
}
