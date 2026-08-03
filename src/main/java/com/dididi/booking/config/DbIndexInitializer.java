package com.dididi.booking.config;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * TỐI ƯU HIỆU NĂNG: tạo INDEX cho các cột hay lọc/join (idempotent — kiểm tra
 * information_schema trước khi tạo, khởi động lại bao nhiêu lần cũng an toàn).
 *
 * Vì sao không dùng @Index trên entity? ddl-auto=update của Hibernate không đảm bảo
 * bổ sung index cho bảng ĐÃ tồn tại — tự quản bằng native SQL vừa chắc chắn vừa
 * nhìn thấy rõ danh sách index trong code (dễ giải thích khi phỏng vấn).
 *
 * Tác dụng chính:
 *  - reviews(target_type, target_id, status): query AVG rating theo lô (fix M5) + trang chi tiết.
 *  - bookings(created_at) & (status, created_at): dashboard/báo cáo doanh thu theo thời gian.
 *  - payments(booking_id): join thanh toán theo đơn.
 *  - hotels(active, city): các luồng lọc theo thành phố.
 *  - flights(from_airport, to_airport, departure_time): tìm chuyến theo tuyến + ngày.
 *  - social_posts(status, visibility, id): feed/explore cộng đồng.
 */
@Component
@Order(5)
public class DbIndexInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DbIndexInitializer.class);

    private static final String[][] INDEXES = {
        {"hotels",       "idx_hotels_active_city",     "CREATE INDEX idx_hotels_active_city ON hotels (active, city)"},
        {"reviews",      "idx_reviews_target",         "CREATE INDEX idx_reviews_target ON reviews (target_type, target_id, status)"},
        {"bookings",     "idx_bookings_created_at",    "CREATE INDEX idx_bookings_created_at ON bookings (created_at)"},
        {"bookings",     "idx_bookings_status_created","CREATE INDEX idx_bookings_status_created ON bookings (status, created_at)"},
        {"payments",     "idx_payments_booking",       "CREATE INDEX idx_payments_booking ON payments (booking_id)"},
        {"flights",      "idx_flights_route_time",     "CREATE INDEX idx_flights_route_time ON flights (from_airport, to_airport, departure_time)"},
        {"social_posts", "idx_posts_status_vis",       "CREATE INDEX idx_posts_status_vis ON social_posts (status, visibility, id)"},
    };

    private final EntityManager em;

    public DbIndexInitializer(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void run(String... args) {
        int created = 0;
        for (String[] ix : INDEXES) {
            try {
                Number n = (Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM information_schema.statistics " +
                        "WHERE table_schema = DATABASE() AND table_name = ?1 AND index_name = ?2")
                        .setParameter(1, ix[0]).setParameter(2, ix[1]).getSingleResult();
                if (n.longValue() == 0) {
                    em.createNativeQuery(ix[2]).executeUpdate();
                    created++;
                    log.info("[index] Đã tạo {} trên bảng {}", ix[1], ix[0]);
                }
            } catch (Exception e) {
                // Thiếu bảng (lần chạy đầu, ddl-auto chưa kịp) hay thiếu quyền -> chỉ cảnh báo, không chặn khởi động.
                log.warn("[index] Bỏ qua {}: {}", ix[1], e.getMessage());
            }
        }
        if (created > 0) log.info("[index] Hoàn tất: tạo mới {} index.", created);
    }
}
