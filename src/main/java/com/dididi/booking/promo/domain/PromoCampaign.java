package com.dididi.booking.promo.domain;

import com.dididi.booking.common.domain.BaseEntity;
import com.dididi.booking.voucher.domain.VoucherDiscountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

/**
 * CẤU HÌNH 1 chương trình khuyến mãi cá nhân hoá (admin bật/tắt + chỉnh mức giảm ngay trên giao diện,
 * không phải sửa code/deploy lại). Mỗi {@link PromoCampaignType} có đúng 1 dòng, tạo sẵn lúc khởi động.
 *
 * Khi đủ điều kiện, hệ thống sinh 1 {@code Voucher} RIÊNG cho khách (ownerUserId = khách đó) theo
 * đúng các thông số dưới đây, và ghi lại 1 {@link PromoGrant} để không tặng trùng.
 */
@Entity
@Table(name = "promo_campaign",
        uniqueConstraints = @UniqueConstraint(name = "uk_promo_campaign_type", columnNames = "type"))
public class PromoCampaign extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PromoCampaignType type;

    /** Bật/tắt chương trình. Tắt thì scheduler bỏ qua hoàn toàn. */
    @Column(nullable = false)
    private boolean enabled = true;

    /** Tên hiển thị cho khách (vd "Quà sinh nhật từ Dididi"). */
    @Column(nullable = false, length = 120)
    private String title;

    /** Mô tả ngắn hiện trên voucher + thông báo. */
    @Column(length = 200)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 16)
    private VoucherDiscountType discountType = VoucherDiscountType.PERCENT;

    /** PERCENT: số % (vd 10). FIXED: số tiền VND (vd 200000). */
    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue = BigDecimal.ZERO;

    /** Trần giảm khi tính theo %, null = không trần. */
    @Column(name = "max_discount", precision = 18, scale = 2)
    private BigDecimal maxDiscount;

    /** Đơn tối thiểu để dùng được, null = không yêu cầu. */
    @Column(name = "min_order_amount", precision = 18, scale = 2)
    private BigDecimal minOrderAmount;

    /** Voucher tặng có hạn dùng bao nhiêu ngày kể từ lúc tặng. */
    @Column(name = "valid_days", nullable = false)
    private int validDays = 30;

    /**
     * Ý nghĩa tuỳ chương trình:
     *  - WIN_BACK: bao nhiêu ngày KHÔNG đặt đơn thì được coi là "khách cũ cần kéo về" (vd 90).
     *  - TIER_REWARD: khoảng cách giữa 2 lần tặng (vd 90 = mỗi quý 1 lần).
     *  - BIRTHDAY / WELCOME: không dùng.
     */
    @Column(name = "threshold_days", nullable = false)
    private int thresholdDays = 90;

    /** TIER_REWARD: hạng tối thiểu được nhận (GOLD / PLATINUM / DIAMOND). */
    @Column(name = "min_tier", length = 16)
    private String minTier = "GOLD";

    public PromoCampaignType getType() { return type; }
    public void setType(PromoCampaignType type) { this.type = type; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public VoucherDiscountType getDiscountType() { return discountType; }
    public void setDiscountType(VoucherDiscountType discountType) { this.discountType = discountType; }

    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }

    public BigDecimal getMaxDiscount() { return maxDiscount; }
    public void setMaxDiscount(BigDecimal maxDiscount) { this.maxDiscount = maxDiscount; }

    public BigDecimal getMinOrderAmount() { return minOrderAmount; }
    public void setMinOrderAmount(BigDecimal minOrderAmount) { this.minOrderAmount = minOrderAmount; }

    public int getValidDays() { return validDays; }
    public void setValidDays(int validDays) { this.validDays = validDays; }

    public int getThresholdDays() { return thresholdDays; }
    public void setThresholdDays(int thresholdDays) { this.thresholdDays = thresholdDays; }

    public String getMinTier() { return minTier; }
    public void setMinTier(String minTier) { this.minTier = minTier; }
}
