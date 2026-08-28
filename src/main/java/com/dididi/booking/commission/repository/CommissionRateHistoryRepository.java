package com.dididi.booking.commission.repository;

import com.dididi.booking.commission.domain.CommissionRateHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CommissionRateHistoryRepository extends JpaRepository<CommissionRateHistory, Long> {

    /** Tỷ lệ có hiệu lực tại một mốc ngày (bản ghi mới nhất còn <= mốc đó). */
    Optional<CommissionRateHistory> findTopByEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(LocalDate at);

    List<CommissionRateHistory> findAllByOrderByEffectiveFromDescIdDesc();
}
