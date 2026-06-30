package com.dididi.booking.voucher.domain;

import com.dididi.booking.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * BP-VOU-02: ban ghi "1 user da dung 1 voucher" — enforce gioi han luot/user MANG TINH NGUYEN TU
 * bang UNIQUE constraint (voucher_code, user_id) o tang DB, thay vi dem booking CONFIRMED (co race).
 * Insert tai luc apply; 2 request song song se co 1 cai dinh DataIntegrityViolationException.
 */
@Entity
@Table(name = "voucher_redemption",
        uniqueConstraints = @UniqueConstraint(name = "uk_voucher_redemption_code_user",
                columnNames = {"voucher_code", "user_id"}))
public class VoucherRedemption extends BaseEntity {

    @Column(name = "voucher_code", nullable = false, length = 40)
    private String voucherCode;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Ma don da ap voucher (de tham chieu / dao nguoc khi can). */
    @Column(name = "booking_id")
    private Long bookingId;

    public VoucherRedemption() {
    }

    public VoucherRedemption(String voucherCode, Long userId, Long bookingId) {
        this.voucherCode = voucherCode;
        this.userId = userId;
        this.bookingId = bookingId;
    }

    public String getVoucherCode() { return voucherCode; }
    public void setVoucherCode(String voucherCode) { this.voucherCode = voucherCode; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
}
