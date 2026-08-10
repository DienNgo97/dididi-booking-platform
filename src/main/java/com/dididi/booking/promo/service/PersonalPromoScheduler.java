package com.dididi.booking.promo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Chạy các chương trình khuyến mãi cá nhân hoá:
 *  - 08:05 mỗi sáng (giờ VN): quét sinh nhật hôm nay + khách lâu không đặt + tri ân hạng.
 *  - Sau khi khởi động 1 phút: chạy 1 lượt để dữ liệu demo có ngay (tiện chấm đồ án).
 * Idempotent: đã tặng trong chu kỳ thì bỏ qua, nên chạy nhiều lần vô hại.
 */
@Component
public class PersonalPromoScheduler {

    private static final Logger log = LoggerFactory.getLogger(PersonalPromoScheduler.class);

    private final PersonalPromoService promoService;

    public PersonalPromoScheduler(PersonalPromoService promoService) {
        this.promoService = promoService;
    }

    @Scheduled(cron = "0 5 8 * * *", zone = "Asia/Ho_Chi_Minh")
    public void daily() {
        run("hằng ngày");
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(initialDelay = 60_000, fixedDelay = 6 * 60 * 60 * 1000L)
    public void afterStartup() {
        run("sau khởi động");
    }

    private void run(String label) {
        try {
            int n = promoService.runAll();
            if (n > 0) log.info("[promo] Đợt {}: đã tặng {} voucher cá nhân hoá.", label, n);
        } catch (Exception e) {
            log.warn("[promo] Đợt {} lỗi: {}", label, e.toString());
        }
    }
}
