package com.dididi.booking.commission.repository;

import com.dididi.booking.commission.domain.CommissionConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommissionConfigRepository extends JpaRepository<CommissionConfig, Long> {
    Optional<CommissionConfig> findTopByOrderByIdAsc();
}
