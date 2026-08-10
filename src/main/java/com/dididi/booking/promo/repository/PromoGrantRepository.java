package com.dididi.booking.promo.repository;

import com.dididi.booking.promo.domain.PromoGrant;
import com.dididi.booking.promo.domain.PromoCampaignType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PromoGrantRepository extends JpaRepository<PromoGrant, Long> {

    boolean existsByTypeAndUserIdAndCycleKey(PromoCampaignType type, Long userId, String cycleKey);

    List<PromoGrant> findByUserIdOrderByIdDesc(Long userId);

    /** Lần gần nhất user được tặng theo 1 chương trình (dùng cho TIER_REWARD / WIN_BACK). */
    java.util.Optional<PromoGrant> findFirstByTypeAndUserIdOrderByIdDesc(PromoCampaignType type, Long userId);

    // ---- Admin ----
    Page<PromoGrant> findAllByOrderByIdDesc(Pageable pageable);

    Page<PromoGrant> findByTypeOrderByIdDesc(PromoCampaignType type, Pageable pageable);

    long countByType(PromoCampaignType type);

    long countByTypeAndCreatedAtAfter(PromoCampaignType type, Instant after);
}
