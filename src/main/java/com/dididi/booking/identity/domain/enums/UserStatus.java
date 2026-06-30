package com.dididi.booking.identity.domain.enums;

public enum UserStatus {
    ACTIVE,
    INACTIVE,
    LOCKED,
    /** Tài khoản đã được chủ tài khoản tự xoá (soft delete) — không đăng nhập được nữa. */
    CLOSED
}
