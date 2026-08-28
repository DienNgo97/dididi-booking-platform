package com.dididi.booking.settlement.repository;

import com.dididi.booking.settlement.domain.SettlementAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementAdjustmentRepository extends JpaRepository<SettlementAdjustment, Long> {

    /** Các khoản điều chỉnh còn treo của 1 đối tác — sẽ trừ vào kỳ chốt kế tiếp. */
    List<SettlementAdjustment> findByPartnerCodeAndAppliedPeriodIsNullOrderByIdAsc(String partnerCode);

    List<SettlementAdjustment> findByAppliedPeriodIsNullOrderByIdAsc();

    List<SettlementAdjustment> findByAppliedPeriodOrderByIdAsc(String appliedPeriod);

    boolean existsByBookingId(Long bookingId);
}
