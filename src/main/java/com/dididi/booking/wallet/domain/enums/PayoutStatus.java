package com.dididi.booking.wallet.domain.enums;

/** Vòng đời một yêu cầu rút tiền của vendor. */
public enum PayoutStatus {
    /** Vendor vừa tạo — tiền đã bị GIỮ CHỖ khỏi số khả dụng. Vendor còn tự huỷ được ở bước này. */
    REQUESTED,
    /** Bộ xử lý chi tiền đang chạy (mock ngân hàng ở dev; prod là admin thao tác tay). */
    PROCESSING,
    /** Đã chi thành công — sinh bút toán PAYOUT âm trong sổ cái. */
    PAID,
    /** Chi thất bại — tiền tự nhả về khả dụng, vendor tạo lại được. */
    FAILED,
    /** Vendor tự huỷ khi còn REQUESTED — tiền nhả về khả dụng. */
    CANCELLED
}
