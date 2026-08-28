package com.dididi.booking.booking;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.booking.service.BookingService;
import com.dididi.booking.payment.domain.enums.PaymentStatus;
import com.dididi.booking.payment.repository.PaymentRepository;
import com.dididi.booking.payment.service.PaymentReconciliationService;
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
    private final PaymentRepository paymentRepository;
    private final PaymentReconciliationService reconciliationService;

    public PaymentExpiryScheduler(BookingRepository bookingRepository, BookingService bookingService,
                                  PaymentRepository paymentRepository,
                                  PaymentReconciliationService reconciliationService) {
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.paymentRepository = paymentRepository;
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(fixedRate = 300_000)   // 5 phut
    public void expireStalePendingPayments() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(BookingService.HOLD_MINUTES));
        List<Booking> stale = bookingRepository.findByStatusAndCreatedAtBefore(BookingStatus.PENDING_PAYMENT, cutoff);
        if (stale.isEmpty()) return;
        int failed = 0;
        for (Booking b : stale) {
            // BP-PAY-05: KHONG giet don da THANH TOAN (callback xac nhan tre/mat) -> bo qua neu Payment da PAID.
            var payment = paymentRepository.findByBookingId(b.getId()).orElse(null);
            boolean alreadyPaid = payment != null && payment.getStatus() == PaymentStatus.PAID;
            if (alreadyPaid) continue;

            // Co hoi cuoi cung truoc khi giet don: HOI THANG VNPay xem khach da tra tien chua.
            // Truong hop that: khach tra tien xong tat tab, VNPay khong goi IPN vao duoc (localhost/NAT)
            // -> Payment van PENDING -> truoc day don bi giet du khach DA MAT TIEN.
            if (payment != null && "VNPAY".equals(payment.getMethod())) {
                PaymentReconciliationService.Outcome outcome;
                try {
                    outcome = reconciliationService.reconcile(payment);
                } catch (Exception ex) {
                    log.warn("Loi khi doi soat {}: {}", b.getPublicCode(), ex.toString());
                    outcome = PaymentReconciliationService.Outcome.UNREACHABLE;
                }
                if (outcome == PaymentReconciliationService.Outcome.CONFIRMED) {
                    log.warn("Don {} da duoc thanh toan tai VNPay nhung callback bi mat — da xac nhan thay vi huy",
                            b.getPublicCode());
                    continue;
                }
                // CHUA HOI DUOC VNPay (cong loi / bi chan vi hoi trung / chu ky sai) thi KHONG duoc
                // suy ra la "khach chua tra tien" — do la cach giet nham don da mat tien.
                // Tam hoan huy, NHUNG chi trong mot cua so co han: neu cu hoan mai thi don PENDING
                // se ket vinh vien khi VNPay hong lau — dung lai cai bug ma minh vua chua.
                // P0-3: khách đang nhập OTP -> hoãn huỷ như trường hợp không hỏi được (có giới hạn).
                if (outcome == PaymentReconciliationService.Outcome.IN_PROGRESS
                        || outcome == PaymentReconciliationService.Outcome.UNREACHABLE) {
                    boolean stillWorthWaiting = b.getCreatedAt() != null && b.getCreatedAt()
                            .isAfter(Instant.now().minus(Duration.ofMinutes(BookingService.HOLD_MINUTES * 2L)));
                    if (stillWorthWaiting) {
                        log.warn("Chua hoi duoc VNPay ve {} truoc khi het han — tam hoan huy, se thu lai",
                                b.getPublicCode());
                        continue;
                    }
                    log.warn("Van chua ngã ngũ voi VNPay ve {} sau {} phut ({}) — huy theo quy trinh cu",
                            b.getPublicCode(), BookingService.HOLD_MINUTES * 2, outcome);
                }
            }
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
