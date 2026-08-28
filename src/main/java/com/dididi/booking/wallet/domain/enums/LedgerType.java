package com.dididi.booking.wallet.domain.enums;

/** Loại bút toán trong sổ cái ví vendor. */
public enum LedgerType {
    /** Doanh thu 1 đơn CONFIRMED: net = gross − hoa hồng (rate CHỐT tại thời điểm ghi). */
    EARNING,
    /** Đảo bút toán EARNING khi đơn bị huỷ/hoàn (luôn hoàn toàn phần) — net âm đúng bằng bút toán gốc. */
    REVERSAL,
    /** Chi tiền rút thành công (PAID) — net âm bằng số tiền đã chi. */
    PAYOUT
}
