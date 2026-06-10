package com.dididi.booking.loyalty.repository;

import com.dididi.booking.loyalty.domain.LoyaltyTransaction;
import com.dididi.booking.loyalty.domain.LoyaltyTxnType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, Long> {

    List<LoyaltyTransaction> findByUserIdOrderByIdDesc(Long userId);

    boolean existsByBookingIdAndType(Long bookingId, LoyaltyTxnType type);

    @Query("select coalesce(sum(t.points),0) from LoyaltyTransaction t where t.userId = :userId")
    int balance(@Param("userId") Long userId);

    @Query("select coalesce(sum(t.points),0) from LoyaltyTransaction t where t.userId = :userId and t.type = :type")
    int sumByType(@Param("userId") Long userId, @Param("type") LoyaltyTxnType type);
}
