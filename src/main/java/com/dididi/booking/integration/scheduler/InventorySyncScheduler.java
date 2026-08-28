package com.dididi.booking.integration.scheduler;

import com.dididi.booking.integration.service.SyncJobOrchestrator;
import com.dididi.booking.ops.service.JobHealthService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Chay sync moi 15 phut. Lan dau chay 10s sau khi app khoi dong (de demo/DoD).
 *
 * <p>P1-12: đồng bộ PMS/hãng bay hỏng nhiều lần liên tiếp nghĩa là kho phòng/vé đang lệch với
 * nguồn thật — phải báo cho người vận hành, không chỉ nằm im trong log.</p>
 */
@Component
public class InventorySyncScheduler {

    private final SyncJobOrchestrator orchestrator;
    private final JobHealthService jobHealth;

    public InventorySyncScheduler(SyncJobOrchestrator orchestrator, JobHealthService jobHealth) {
        this.orchestrator = orchestrator;
        this.jobHealth = jobHealth;
    }

    @Scheduled(initialDelay = 10_000, fixedRate = 15 * 60 * 1000)
    public void scheduledSync() {
        try {
            orchestrator.syncAll();
            jobHealth.thanhCong("inventory-sync");
        } catch (Exception e) {
            jobHealth.thatBai("inventory-sync", e);
        }
    }
}
