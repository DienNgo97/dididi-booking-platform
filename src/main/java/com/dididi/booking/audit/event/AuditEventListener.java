package com.dididi.booking.audit.event;

import com.dididi.booking.audit.service.AuditService;
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

    private final AuditService auditService;

    public AuditEventListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAuditEvent(AuditEvent e) {
        auditService.log(e.actorUserId(), e.action(), e.targetType(), e.targetId(), e.detail());
    }
}
