package com.dididi.booking.review.domain.enums;

/** Trang thai kiem duyet review. Cong khai chi hien PUBLISHED. */
public enum ReviewStatus {
    PENDING,    // cho duyet (khi app.review.auto-publish=false)
    PUBLISHED,  // dang hien thi
    HIDDEN      // bi an boi admin
}
