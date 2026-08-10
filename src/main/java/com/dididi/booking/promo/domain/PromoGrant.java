package com.dididi.booking.promo.domain;

import com.dididi.booking.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * LỊCH SỬ PHÁT ưu đãi cá nhân hoá: mỗi dòng = 1 lần tặng voucher cho 1 khách.
 *
 * CHỐNG TẶNG TRÙNG bằng unique (type, user_id, cycle_key) — cycleKey là "chu kỳ" của chương trình:
 *   BIRTHDAY     -> "2026"      (mỗi năm 1 lần)
 *   WIN_BACK     -> "2026-08"   (mỗi tháng tối đa 1 lần)
 *   TIER_REWARD  -> "2026-Q3"   (mỗi chu kỳ theo thresholdDays, quy về quý cho dễ đọc)
 *   WELCOME      -> "ONCE"      (đúng 1 lần/đời)
 * Insert trùng bị DB chặn -> job chạy lại nhiều lần trong ngày vẫn an toàn.
 */
@Entity
@Table(name = "promo_grant",
        uniqueConstraints = @UniqueConstraint(name = "uk_promo_grant_cycle",
                columnNames = {"type", "user_id", "cycle_key"}),
        indexes = {
                @Index(name = "idx_promo_grant_user", columnList = "user_id,id"),
                @Index(name = "idx_promo_grant_type", columnList = "type,id")
        })
public class PromoGrant extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PromoCampaignType type;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "cycle_key", nullable = false, length = 16)
    private String cycleKey;

    /** Mã voucher đã tặng (trỏ sang bảng voucher). */
    @Column(name = "voucher_code", nullable = false, length = 40)
    private String voucherCode;

    /** Ghi chú hiển thị cho admin (vd "Sinh nhật 12/08 · giảm 10%"). */
    @Column(length = 200)
    private String note;

    public PromoCampaignType getType() { return type; }
    public void setType(PromoCampaignType type) { this.type = type; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getCycleKey() { return cycleKey; }
    public void setCycleKey(String cycleKey) { this.cycleKey = cycleKey; }

    public String getVoucherCode() { return voucherCode; }
    public void setVoucherCode(String voucherCode) { this.voucherCode = voucherCode; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
