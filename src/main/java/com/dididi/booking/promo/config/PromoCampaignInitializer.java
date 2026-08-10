package com.dididi.booking.promo.config;

import com.dididi.booking.promo.domain.PromoCampaign;
import com.dididi.booking.promo.domain.PromoCampaignType;
import com.dididi.booking.promo.repository.PromoCampaignRepository;
import com.dididi.booking.voucher.domain.VoucherDiscountType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Tạo sẵn 4 chương trình khuyến mãi cá nhân hoá với thông số mặc định (chạy 1 lần, idempotent:
 * chương trình nào đã có thì giữ nguyên cấu hình admin đã chỉnh, KHÔNG ghi đè).
 */
@Component
@Order(6)
public class PromoCampaignInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PromoCampaignInitializer.class);

    private final PromoCampaignRepository repository;

    public PromoCampaignInitializer(PromoCampaignRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        int created = 0;
        created += ensure(PromoCampaignType.BIRTHDAY, "Quà sinh nhật từ Dididi",
                "Chúc mừng sinh nhật! Tặng bạn mã giảm giá dùng trong tháng sinh nhật.",
                VoucherDiscountType.PERCENT, BigDecimal.valueOf(15), BigDecimal.valueOf(500_000),
                null, 30, 0, null);
        created += ensure(PromoCampaignType.WIN_BACK, "Chào mừng bạn quay lại",
                "Lâu rồi bạn chưa đi đâu! Tặng mã giảm giá cho chuyến kế tiếp.",
                VoucherDiscountType.FIXED, BigDecimal.valueOf(200_000), null,
                BigDecimal.valueOf(1_000_000), 21, 90, null);
        created += ensure(PromoCampaignType.TIER_REWARD, "Tri ân hạng thành viên",
                "Cảm ơn bạn đã đồng hành cùng Dididi — ưu đãi riêng cho hạng của bạn.",
                VoucherDiscountType.PERCENT, BigDecimal.valueOf(10), BigDecimal.valueOf(800_000),
                null, 60, 90, "GOLD");
        created += ensure(PromoCampaignType.WELCOME, "Quà chào mừng khách mới",
                "Chào mừng bạn đến Dididi! Giảm ngay cho đơn đầu tiên.",
                VoucherDiscountType.FIXED, BigDecimal.valueOf(100_000), null,
                BigDecimal.valueOf(500_000), 30, 0, null);
        if (created > 0) log.info("[promo] Đã tạo {} chương trình khuyến mãi cá nhân hoá mặc định.", created);
    }

    private int ensure(PromoCampaignType type, String title, String desc,
                       VoucherDiscountType dType, BigDecimal value, BigDecimal maxDiscount,
                       BigDecimal minOrder, int validDays, int thresholdDays, String minTier) {
        if (repository.findByType(type).isPresent()) return 0;
        PromoCampaign c = new PromoCampaign();
        c.setType(type);
        c.setEnabled(true);
        c.setTitle(title);
        c.setDescription(desc);
        c.setDiscountType(dType);
        c.setDiscountValue(value);
        c.setMaxDiscount(maxDiscount);
        c.setMinOrderAmount(minOrder);
        c.setValidDays(validDays);
        c.setThresholdDays(thresholdDays);
        c.setMinTier(minTier);
        repository.save(c);
        return 1;
    }
}
