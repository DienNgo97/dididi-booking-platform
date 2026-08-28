package com.dididi.booking.payment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hẹn giờ cho việc đối soát thanh toán với VNPay.
 *
 * Vì sao tách khỏi {@link PaymentReconciliationService}: nếu đặt @Scheduled ngay trong service đó,
 * vòng lặp sẽ gọi {@code this.reconcile(...)} — đi thẳng vào đối tượng thật, KHÔNG qua proxy của
 * Spring, khiến @Transactional trên reconcile() mất tác dụng mà không báo lỗi gì.
 * Dự án đã dính đúng cái bẫy này một lần (BP-INT-01: @Transactional trên syncFlights vô hiệu vì
 * self-invocation), nên lần này tách bean ngay từ đầu.
 *
 * Chu kỳ 2 phút: phải dày hơn hẳn mốc hết hạn giữ chỗ 20 phút, để một giao dịch đã trả tiền
 * luôn được nhận ra trước khi scheduler hết hạn định huỷ đơn.
 */
@Component
public class PaymentReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationScheduler.class);

    private final PaymentReconciliationService service;
    private final com.dididi.booking.ops.service.JobHealthService jobHealth;

    public PaymentReconciliationScheduler(PaymentReconciliationService service,
                                          com.dididi.booking.ops.service.JobHealthService jobHealth) {
        this.service = service;
        this.jobHealth = jobHealth;
    }

    @Scheduled(fixedRate = 120_000, initialDelay = 60_000)
    public void run() {
        try {
            service.sweep();
            jobHealth.thanhCong("vnpay-reconcile");
        } catch (Exception ex) {
            // Không bao giờ để một lần đối soát hỏng làm chết luồng scheduler.
            // P1-12: nhưng hỏng LIÊN TIẾP thì phải báo động — đối soát chết nghĩa là đơn đã trả tiền
            // có thể bị job hết hạn giết mà không ai hay.
            log.warn("[Đối soát VNPay] vòng quét lỗi: {}", ex.toString());
            jobHealth.thatBai("vnpay-reconcile", ex);
        }
    }
}
