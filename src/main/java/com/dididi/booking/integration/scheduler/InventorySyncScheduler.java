package com.dididi.booking.integration.scheduler;

import com.dididi.booking.integration.service.SyncJobOrchestrator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Chay sync moi 15 phut. Lan dau chay 10s sau khi app khoi dong (de demo/DoD).
 */
@Component
public class InventorySyncScheduler {

    private final SyncJobOrchestrator orchestrator;

    public InventorySyncScheduler(SyncJobOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Scheduled(initialDelay = 10_000, fixedRate = 15 * 60 * 1000)
    public void scheduledSync() {
        orchestrator.syncAll();
    }
}
