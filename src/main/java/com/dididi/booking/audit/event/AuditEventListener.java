package com.dididi.booking.audit.event;

import com.dididi.booking.audit.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Ghi audit BAT DONG BO va CHI sau khi giao dich da COMMIT.
 * fallbackExecution=true: neu su kien phat ngoai giao dich thi van xu ly (ghi ngay).
 */
@Component
public class AuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

    private final AuditService auditService;

    public AuditEventListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAuditEvent(AuditEvent e) {
        // Audit chay bat dong bo SAU commit: phai tu nuot loi + GHI LOG. Neu khong, loi (vd detail qua dai,
        // DB tre) bi mat am tham -> hanh dong nhay cam thanh cong nhung KHONG co dau vet audit ma khong ai biet.
        try {
            auditService.log(e.actorUserId(), e.action(), e.targetType(), e.targetId(), e.detail());
        } catch (Exception ex) {
            log.error("Ghi audit that bai (action={}, target={}:{}): {}",
                    e.action(), e.targetType(), e.targetId(), ex.toString());
        }
    }
}
