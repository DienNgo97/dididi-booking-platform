package com.dididi.booking.integration.repository;

import com.dididi.booking.integration.domain.entity.SyncLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {
}
