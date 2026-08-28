package com.dididi.booking.ops.repository;

import com.dididi.booking.ops.domain.OpsAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OpsAlertRepository extends JpaRepository<OpsAlert, Long> {

    Optional<OpsAlert> findByDedupeKey(String dedupeKey);

    Page<OpsAlert> findAllByOrderByStatusAscIdDesc(Pageable pageable);

    Page<OpsAlert> findByStatusOrderByIdDesc(OpsAlert.Status status, Pageable pageable);

    long countByStatus(OpsAlert.Status status);
}
