package com.dididi.booking.ops.service;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.booking.service.BookingService;
import com.dididi.booking.ops.domain.OpsAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * WATCHDOG VẬN HÀNH (P0-3 + P0-4, 28/08/2026).
 *
 * Triết lý giống phương trình dòng tiền của đối soát: thay vì tin rằng các luồng luôn chạy đúng,
 * ta ĐỊNH NGHĨA trạng thái "đúng" rồi quét tìm cái lệch — lệch thì tự cứu, cứu không được thì
 * báo động cho người thật xử lý. Chạy mỗi 5 phút.
 *
 * Hai bất biến được canh:
 *  1. Payment PAID  =>  Booking phải CONFIRMED. (khách trả tiền thì phải có dịch vụ)
 *  2. Đơn vé CONFIRMED có chọn ghế  =>  ghế đã chốt với hãng. (đã thu tiền thì ghế phải là của khách)
 */
@Component
public class OpsWatchdogScheduler {

    private static final Logger log = LoggerFactory.getLogger(OpsWatchdogScheduler.class);

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final OpsAlertService alerts;

    /**
     * MỐC BẮT ĐẦU GIÁM SÁT. Dữ liệu phát sinh TRƯỚC mốc này (đơn seed, đơn thời chưa có watchdog)
     * không được phép sinh hàng trăm cảnh báo lẻ — vừa ngập vừa không sửa được từng cái.
     * Chúng được gộp thành MỘT cảnh báo tổng hợp "nợ dữ liệu lịch sử" để admin rà một lượt.
     */
    @Value("${app.ops.watchdog-since:2026-08-28}")
    private String watchdogSince;

    public OpsWatchdogScheduler(BookingRepository bookingRepository, BookingService bookingService,
                                OpsAlertService alerts) {
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.alerts = alerts;
    }

    @Scheduled(fixedDelay = 300_000L, initialDelay = 90_000L)
    public void scan() {
        try {
            scanPaidButNotConfirmed();
        } catch (Exception e) {
            log.error("[ops] Watchdog tiền/đơn lỗi: {}", e.toString());
        }
        try {
            scanUnconfirmedSeats();
        } catch (Exception e) {
            log.error("[ops] Watchdog ghế lỗi: {}", e.toString());
        }
    }

    private Instant sinceInstant() {
        try {
            return LocalDate.parse(watchdogSince).atStartOfDay(ZoneId.systemDefault()).toInstant();
        } catch (Exception e) {
            return Instant.EPOCH;   // cấu hình sai thì giám sát tất — thà ồn còn hơn mù
        }
    }

    /**
     * Dữ liệu KHÔNG phải sự cố vận hành mới: (a) đơn tạo trước mốc bật giám sát, hoặc
     * (b) đơn có ngày tạo ở TƯƠNG LAI — không đơn thật nào được đặt ở tương lai, đó là dấu hiệu
     * dữ liệu seed/nhập tay. Bắn CRITICAL cho chúng chỉ làm loãng cảnh báo thật.
     */
    private boolean laDuLieuCu(Booking b) {
        Instant createdAt = b.getCreatedAt();
        if (createdAt == null) return false;                       // không rõ thì cứ cảnh báo, thà ồn còn hơn sót
        return createdAt.isBefore(sinceInstant())
                || createdAt.isAfter(Instant.now().plusSeconds(300));   // 5' đệm cho lệch đồng hồ
    }

    /** Gộp nợ dữ liệu lịch sử thành MỘT dòng (dedupeKey cố định) thay vì N cảnh báo lẻ. */
    private void raiseLegacySummary(OpsAlert.Type type, int count, String what) {
        if (count == 0) return;
        alerts.raise(type, OpsAlert.Severity.WARNING, null, "LEGACY:" + type.name(),
                count + " đơn cũ/dữ liệu seed (trước mốc giám sát " + watchdogSince
                        + ", hoặc có ngày tạo bất thường) đang " + what
                        + " — nợ dữ liệu lịch sử, không phải sự cố mới.",
                "Rà soát một lượt bằng truy vấn/đối soát; xong thì đánh dấu đã xử lý. "
                        + "Đơn phát sinh từ sau mốc trên sẽ được cảnh báo riêng từng đơn.");
    }

    /** Bất biến 1: đã thu tiền thì đơn phải sống. */
    private void scanPaidButNotConfirmed() {
        List<Booking> lech = bookingRepository.findPaidButNotConfirmed();
        int legacy = 0;
        for (Booking b : lech) {
            if (laDuLieuCu(b)) { legacy++; continue; }
            if (b.getStatus() == BookingStatus.PENDING_PAYMENT) {
                // CỨU ĐƯỢC: callback bị mất nhưng chỗ vẫn đang giữ -> xác nhận luôn cho khách.
                try {
                    bookingService.markConfirmed(b);
                    alerts.autoResolve(OpsAlert.Type.PAYMENT_BOOKING_MISMATCH, b.getId(),
                            "Đã tự xác nhận lại đơn (tiền có, chỗ còn giữ)");
                    log.warn("[ops] Đơn {} đã trả tiền nhưng chưa xác nhận — watchdog đã tự xác nhận.",
                            b.getPublicCode());
                    continue;
                } catch (Exception e) {
                    log.error("[ops] Không tự xác nhận được đơn {}: {}", b.getPublicCode(), e.toString());
                }
            }
            // KHÔNG cứu tự động được (đơn đã FAILED/CANCELLED, chỗ có thể đã bán cho người khác)
            // -> phải có người quyết định: xếp lại chỗ hay hoàn tiền cho khách.
            alerts.raise(OpsAlert.Type.PAYMENT_BOOKING_MISMATCH, OpsAlert.Severity.CRITICAL,
                    b.getId(), b.getPublicCode(),
                    "Khách đã thanh toán nhưng đơn đang ở trạng thái " + b.getStatus()
                            + " — tiền đã vào tài khoản mà khách không có dịch vụ.",
                    "Kiểm tra còn phòng/ghế không: còn thì xếp lại và xác nhận đơn, hết thì hoàn tiền cho khách.");
        }
        raiseLegacySummary(OpsAlert.Type.PAYMENT_BOOKING_MISMATCH, legacy,
                "ở trạng thái đã thu tiền nhưng đơn không CONFIRMED");
    }

    /** Bất biến 2: vé đã thu tiền thì ghế phải là của khách, không phải đang giữ tạm. */
    private void scanUnconfirmedSeats() {
        List<Booking> chuaChot = bookingRepository.findConfirmedFlightsWithUnconfirmedSeats();
        int legacy = 0;
        for (Booking b : chuaChot) {
            // Đơn cũ: cột seatsConfirmed mới thêm nên mặc định false cho TOÀN BỘ vé cũ — gọi lại hãng
            // cho những đơn này là vô nghĩa (chuyến/giữ chỗ đã hết hạn từ lâu). Chỉ đếm gộp.
            if (laDuLieuCu(b)) { legacy++; continue; }
            boolean ok = false;
            try {
                ok = bookingService.retryConfirmSeats(b);   // thử lại — hãng có thể chỉ lỗi nhất thời
            } catch (Exception e) {
                log.error("[ops] Thử lại xác nhận ghế cho {} lỗi: {}", b.getPublicCode(), e.toString());
            }
            if (ok) {
                alerts.autoResolve(OpsAlert.Type.FLIGHT_SEAT_UNCONFIRMED, b.getId(),
                        "Đã xác nhận được ghế với hãng ở lần thử lại");
                log.info("[ops] Đã chốt được ghế cho đơn {} ở lần thử lại.", b.getPublicCode());
                continue;
            }
            alerts.raise(OpsAlert.Type.FLIGHT_SEAT_UNCONFIRMED, OpsAlert.Severity.CRITICAL,
                    b.getId(), b.getPublicCode(),
                    "Vé đã thu tiền (ghế " + b.getSeatCodes() + ") nhưng chưa xác nhận được với hãng — "
                            + "ghế đang giữ tạm và sẽ bị nhả cho khách khác.",
                    "Liên hệ hãng xác nhận chỗ thủ công; nếu hết chỗ thì đổi chuyến hoặc hoàn tiền cho khách.");
        }
        raiseLegacySummary(OpsAlert.Type.FLIGHT_SEAT_UNCONFIRMED, legacy,
                "chưa có dấu xác nhận ghế với hãng (cột theo dõi mới có từ hôm nay)");
    }
}
