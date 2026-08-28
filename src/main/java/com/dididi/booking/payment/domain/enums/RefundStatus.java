package com.dididi.booking.payment.domain.enums;

/**
 * Trang thai mot lan hoan tien.
 *
 * <p>P1-4: truoc day chi co COMPLETED/FAILED va he thong danh dau COMPLETED ngay khi admin bam nut,
 * du KHONG he goi cong thanh toan — khach nhan email "da hoan tien" trong khi tien chua roi tai khoan.
 * Nay them {@link #PENDING_TRANSFER}: da ghi so va da tra cho/ghe, nhung tien con cho ke toan chuyen.
 * Chi khi ke toan xac nhan da chuyen (co ma giao dich) moi thanh COMPLETED va moi bao khach.</p>
 */
public enum RefundStatus { PENDING_TRANSFER, COMPLETED, FAILED }
