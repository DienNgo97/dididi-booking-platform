package com.dididi.booking.booking;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.booking.service.BookingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * BP-BK-04: quet dinh ky cac don PENDING_PAYMENT da qua HOLD_MINUTES nhung khach khong quay lai thanh toan,
 * chuyen sang FAILED + nha ton kho provider (ghe/phong). Truoc day chi het han "luoi" khi khach mo lai trang,
 * nen row PENDING ket vinh vien, stats phinh, va ghe/phong provider co the ro ri.
 *
 * Chay moi 5 phut. @EnableScheduling da bat o IntegrationConfig.
 */
@Component
public class PaymentExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentExpiryScheduler.class);

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    public PaymentExpiryScheduler(BookingRepository bookingRepository, BookingService bookingService) {
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
    }

    @Scheduled(fixedRate = 300_000)   // 5 phut
    public void expireStalePendingPayments() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(BookingService.HOLD_MINUTES));
        List<Booking> stale = bookingRepository.findByStatusAndCreatedAtBefore(BookingStatus.PENDING_PAYMENT, cutoff);
        if (stale.isEmpty()) return;
        int failed = 0;
        for (Booking b : stale) {
            try {
                bookingService.markPaymentExpired(b);   // doi FAILED + releaseProviderInventory (best-effort)
                failed++;
            } catch (Exception ex) {
                // 1 don loi khong duoc chan cac don con lai.
                log.warn("Expire pending booking {} failed: {}", b.getPublicCode(), ex.toString());
            }
        }
        log.info("Payment-expiry sweep: expired {}/{} stale PENDING_PAYMENT bookings", failed, stale.size());
    }
}
