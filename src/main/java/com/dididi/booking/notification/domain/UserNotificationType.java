package com.dididi.booking.notification.domain;

/**
 * Loại thông báo nền tảng (đặt phòng / thanh toán / huỷ / hoàn tiền / điểm thưởng /
 * đánh giá / lời mời / nhóm). {@code icon} = id symbol trong fragments/icons.html.
 */
public enum UserNotificationType {
    BOOKING_CONFIRMED("ic-circle-check"),
    PAYMENT_SUCCESS("ic-credit-card"),
    PAYMENT_EXPIRED("ic-clock"),
    BOOKING_CANCEL_REQUESTED("ic-clock"),
    BOOKING_CANCEL_APPROVED("ic-circle-check"),
    BOOKING_CANCEL_REJECTED("ic-x"),
    REFUND_COMPLETED("ic-credit-card"),
    LOYALTY_EARNED("ic-gift"),
    LOYALTY_TIER_UP("ic-award"),
    REVIEW_REPLY("ic-message"),
    COMPANY_INVITE("ic-users"),
    COMPANY_INVITE_ACCEPTED("ic-users"),
    GROUP_MEMBER_JOINED("ic-users"),
    GROUP_PAYMENT_SPLIT("ic-credit-card"),
    /** Khuyến mãi cá nhân hoá: sinh nhật / khách quay lại / tri ân hạng / chào mừng. */
    PROMO_GRANTED("ic-gift");

    private final String icon;

    UserNotificationType(String icon) { this.icon = icon; }

    public String getIcon() { return icon; }
}
