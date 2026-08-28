package com.dididi.booking.wallet.service;

import com.dididi.booking.wallet.domain.entity.PayoutRequest;
import com.dididi.booking.wallet.domain.enums.PayoutStatus;
import com.dididi.booking.wallet.repository.PayoutRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;

/**
 * Hai nhịp nền của ví vendor:
 *
 * 1) GHI SỔ (mỗi 60s + ngay khi app sẵn sàng): quét theo TRẠNG THÁI, không hook vào từng
 *    luồng confirm (VNPay return/IPN/mock-pay/reconcile/duyệt B2B/nhóm — đường nào cũng bị
 *    quét trúng). Idempotent nhờ unique constraint → lần chạy đầu chính là BACKFILL lịch sử.
 *
 * 2) MOCK "NGÂN HÀNG" chi tiền (mỗi 15s, chỉ khi app.payout.mock-enabled=true — application-prod.yml
 *    ÉP false): REQUESTED → PROCESSING (nhận việc nguyên tử) → tick sau chốt PAID (~95%) /
 *    FAILED (~5%, demo nhánh lỗi). PROCESSING kẹt quá 10 phút (restart giữa chừng) được nhặt lại.
 */
@Component
public class WalletScheduler {

    private static final Logger log = LoggerFactory.getLogger(WalletScheduler.class);
    private static final int EARNING_BATCH = 500;

    private final VendorWalletService walletService;
    private final PayoutRequestRepository payoutRepository;
    private final com.dididi.booking.ops.service.JobHealthService jobHealth;
    private final Random random = new Random();

    @Value("${app.payout.mock-enabled:false}")
    private boolean mockEnabled;

    @Value("${app.payout.mock-fail-percent:5}")
    private int mockFailPercent;

    public WalletScheduler(VendorWalletService walletService, PayoutRequestRepository payoutRepository,
                           com.dididi.booking.ops.service.JobHealthService jobHealth) {
        this.walletService = walletService;
        this.payoutRepository = payoutRepository;
        this.jobHealth = jobHealth;
    }

    // ---------- 1) Ghi sổ ----------

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            int total = 0, batch;
            do {                                     // backfill lần đầu có thể nhiều hơn 1 lô
                batch = walletService.recordMissingEarnings(EARNING_BATCH);
                total += batch;
            } while (batch == EARNING_BATCH);
            int reversed = walletService.recordMissingReversals();
            if (total > 0 || reversed > 0) {
                log.info("[wallet] Ghi sổ khởi động: +{} EARNING, +{} REVERSAL.", total, reversed);
            }
        } catch (Exception e) {
            log.warn("[wallet] Ghi sổ khởi động lỗi (sẽ thử lại theo nhịp): {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void recordLedger() {
        try {
            int earned = walletService.recordMissingEarnings(EARNING_BATCH);
            int reversed = walletService.recordMissingReversals();
            if (earned > 0 || reversed > 0) {
                log.info("[wallet] Ghi sổ: +{} EARNING, +{} REVERSAL.", earned, reversed);
            }
            jobHealth.thanhCong("wallet-ledger");
        } catch (Exception e) {
            // P1-12: hỏng liên tiếp thì phải có người biết — ghi sổ ví đứng là vendor không thấy tiền.
            jobHealth.thatBai("wallet-ledger", e);
        }
    }

    // ---------- 2) Mock ngân hàng ----------

    @Scheduled(fixedDelay = 15_000L, initialDelay = 20_000L)
    @org.springframework.transaction.annotation.Transactional
    public void processPayouts() {
        if (!mockEnabled) {
            return;                                  // prod: yêu cầu nằm REQUESTED chờ xử lý tay
        }
        try {
            // (a) CHỐT các việc đã nhận từ tick trước (đứng trước bước nhận việc để vendor
            //     thấy trạng thái "Đang xử lý" ít nhất một nhịp 15s) + việc kẹt quá 10 phút.
            List<PayoutRequest> processing = payoutRepository.findByStatusAndUpdatedAtBefore(
                    PayoutStatus.PROCESSING, Instant.now().minus(10, ChronoUnit.SECONDS));
            for (PayoutRequest p : processing) {
                boolean stuck = p.getUpdatedAt() != null
                        && p.getUpdatedAt().isBefore(Instant.now().minus(10, ChronoUnit.MINUTES));
                if (random.nextInt(100) < mockFailPercent && !stuck) {
                    walletService.failPayout(p, "Ngân hàng giả lập từ chối giao dịch (mô phỏng lỗi)");
                    log.info("[wallet] Mock payout #{} FAILED (mô phỏng).", p.getId());
                } else {
                    String ref = "MOCK-" + System.currentTimeMillis() + "-" + p.getId();
                    walletService.completePayout(p, ref);
                    log.info("[wallet] Mock payout #{} PAID ({}).", p.getId(), ref);
                }
            }
            // (b) NHẬN VIỆC: REQUESTED -> PROCESSING (nguyên tử — thua race với vendor tự huỷ thì thôi)
            for (PayoutRequest p : payoutRepository.findByStatusOrderByIdAsc(PayoutStatus.REQUESTED)) {
                payoutRepository.transition(p.getId(), PayoutStatus.REQUESTED, PayoutStatus.PROCESSING);
            }
        } catch (Exception e) {
            log.warn("[wallet] Mock payout lỗi: {}", e.getMessage());
        }
    }
}
