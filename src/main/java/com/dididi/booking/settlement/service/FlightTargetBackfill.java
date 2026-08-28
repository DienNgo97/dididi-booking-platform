package com.dididi.booking.settlement.service;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.flight.domain.entity.Flight;
import com.dididi.booking.flight.repository.FlightRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TỰ LÀNH liên kết đơn vé bay → chuyến bay (phát hiện khi bật Đối soát đối tác, ST5 27/08/2026).
 *
 * DI SẢN: DemoDataSeeder cũ tạo đơn vé "tự chứa" với targetId NGẪU NHIÊN 1..230 ("chỉ để liên kết").
 * Sau các đợt re-sync, bảng flights không còn dải id đó → hai loại hỏng:
 *  1. TREO: targetId trỏ vào chuyến không tồn tại → rơi vào bucket "mồ côi" của phương trình dòng tiền.
 *  2. SAI HÃNG (tệ hơn — âm thầm): targetId tình cờ trúng một chuyến ĐANG tồn tại của hãng khác
 *     → đối soát quy nhầm doanh thu cho hãng đó mà không lưới nào bắt được.
 *
 * SỰ THẬT của các đơn này nằm ở TIÊU ĐỀ ("VN123 HAN→SGN") — seeder sinh hiển thị từ đó.
 * Cách sửa: nối lại targetId theo số hiệu trong tiêu đề; không có chuyến đúng số hiệu thì lấy
 * một chuyến cùng hãng (đối soát chỉ cần đúng HÃNG). Idempotent: sửa xong thì hai query rỗng.
 */
@Component
@Order(1)   // chạy trước các job đọc số liệu khác lúc khởi động
public class FlightTargetBackfill {

    private static final Logger log = LoggerFactory.getLogger(FlightTargetBackfill.class);
    /** Bắt cả title dị dạng "VJVJ501" (mã hãng lặp đôi do một nguồn seed ghép airlineCode + flightNumber đã có prefix). */
    private static final Pattern FLIGHT_NO = Pattern.compile("^([A-Z]{2})(?:\\1)?(\\d{2,4})");

    private final BookingRepository bookingRepository;
    private final FlightRepository flightRepository;
    private final org.springframework.context.ApplicationEventPublisher events;

    /**
     * P1-14 — PHẠM VI SỬA. Job này ghi đè targetId của đơn THẬT nếu dữ liệu tình cờ khớp điều kiện,
     * nên phải có hàng rào: chỉ đụng vào đơn phát sinh TRƯỚC mốc này (di sản seeder cũ).
     * Để trống ("") thì tắt hàng rào — chỉ dùng khi cố ý chạy lại cho toàn bộ dữ liệu.
     */
    @org.springframework.beans.factory.annotation.Value("${app.settlement.backfill-before:2026-08-28}")
    private String backfillBefore;

    /** Chặn trần số đơn sửa mỗi lần khởi động: hỏng hàng loạt thì phải có người nhìn, không tự sửa im lặng. */
    @org.springframework.beans.factory.annotation.Value("${app.settlement.backfill-max:1000}")
    private int backfillMax;

    public FlightTargetBackfill(BookingRepository bookingRepository, FlightRepository flightRepository,
                                org.springframework.context.ApplicationEventPublisher events) {
        this.bookingRepository = bookingRepository;
        this.flightRepository = flightRepository;
        this.events = events;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void repair() {
        try {
            List<Booking> dangling = bookingRepository.findFlightBookingsWithDanglingTarget();
            List<Booking> mismatch = bookingRepository.findFlightBookingsWithAirlineMismatch();
            if (dangling.isEmpty() && mismatch.isEmpty()) {
                return;
            }
            java.time.Instant moc = parseMoc();
            if (moc != null) {
                dangling = dangling.stream().filter(b -> truocMoc(b, moc)).toList();
                mismatch = mismatch.stream().filter(b -> truocMoc(b, moc)).toList();
                if (dangling.isEmpty() && mismatch.isEmpty()) return;
            }
            int tong = dangling.size() + mismatch.size();
            if (tong > backfillMax) {
                log.error("[settlement] {} đơn vé lệch liên kết — VƯỢT trần {} nên KHÔNG tự sửa. "
                        + "Số này lớn bất thường, cần người xem trước khi đụng vào dữ liệu đơn.", tong, backfillMax);
                return;
            }
            Map<String, Long> byNumber = new HashMap<>();   // cache số hiệu -> flightId
            Map<String, Long> byAirline = new HashMap<>();  // cache mã hãng -> flightId bất kỳ
            int fixed = 0, unfixable = 0;
            for (List<Booking> group : List.of(dangling, mismatch)) {
                for (Booking b : group) {
                    Long newTarget = resolve(b.getTitle(), byNumber, byAirline);
                    if (newTarget != null) {
                        b.setTargetId(newTarget);
                        bookingRepository.save(b);
                        fixed++;
                    } else {
                        unfixable++;
                        log.warn("[settlement] Không nối lại được đơn vé {} (title '{}') — cần xem tay.",
                                b.getPublicCode(), b.getTitle());
                    }
                }
            }
            log.info("[settlement] Tự lành liên kết vé bay: nối lại {} đơn ({} treo + {} sai hãng), {} chưa xử lý được.",
                    fixed, dangling.size(), mismatch.size(), unfixable);
            if (fixed > 0) {
                // P1-14: job này SỬA dữ liệu đơn — phải để lại dấu vết, không sửa im lặng.
                events.publishEvent(new com.dididi.booking.audit.event.AuditEvent(null,
                        "FLIGHT_TARGET_BACKFILL", "BOOKING", null,
                        "Tự nối lại targetId cho " + fixed + " đơn vé (" + dangling.size() + " treo, "
                                + mismatch.size() + " sai hãng), " + unfixable + " không xử lý được"
                                + (backfillBefore.isBlank() ? "" : "; chỉ áp cho đơn tạo trước " + backfillBefore)));
            }
        } catch (Exception e) {
            log.warn("[settlement] Tự lành liên kết vé bay lỗi (sẽ thử lại lần khởi động sau): {}", e.getMessage());
        }
    }

    private java.time.Instant parseMoc() {
        if (backfillBefore == null || backfillBefore.isBlank()) return null;   // tắt hàng rào
        try {
            return java.time.LocalDate.parse(backfillBefore.trim())
                    .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
        } catch (Exception e) {
            log.warn("[settlement] app.settlement.backfill-before không hợp lệ ('{}') — bỏ qua hàng rào thời gian.",
                    backfillBefore);
            return null;
        }
    }

    /** Đơn tạo trước mốc = di sản seeder cũ. createdAt null coi như dữ liệu cũ (đơn thật luôn có ngày tạo). */
    private boolean truocMoc(Booking b, java.time.Instant moc) {
        return b.getCreatedAt() == null || b.getCreatedAt().isBefore(moc);
    }

    /** Ưu tiên chuyến ĐÚNG SỐ HIỆU trong tiêu đề; không có thì chuyến CÙNG HÃNG (đối soát cần đúng hãng). */
    private Long resolve(String title, Map<String, Long> byNumber, Map<String, Long> byAirline) {
        if (title == null) return null;
        Matcher m = FLIGHT_NO.matcher(title.trim());
        if (!m.find()) return null;
        String number = m.group(1) + m.group(2);
        String airline = m.group(1);
        Long id = byNumber.computeIfAbsent(number, n ->
                flightRepository.findFirstByFlightNumberOrderByDepartureTimeAsc(n)
                        .map(Flight::getId).orElse(null));
        if (id != null) return id;
        return byAirline.computeIfAbsent(airline, a ->
                flightRepository.findFirstByAirlineCodeOrderByDepartureTimeAsc(a)
                        .map(Flight::getId).orElse(null));
    }
}
