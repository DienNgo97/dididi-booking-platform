package com.dididi.booking.promo.repository;

import com.dididi.booking.promo.domain.PromoCampaign;
import com.dididi.booking.promo.domain.PromoCampaignType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PromoCampaignRepository extends JpaRepository<PromoCampaign, Long> {

    Optional<PromoCampaign> findByType(PromoCampaignType type);

    List<PromoCampaign> findAllByOrderByIdAsc();
}
