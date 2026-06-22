package com.dididi.booking.loyalty.repository;

import com.dididi.booking.loyalty.domain.LoyaltyTransaction;
import com.dididi.booking.loyalty.domain.LoyaltyTxnType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, Long> {

    List<LoyaltyTransaction> findByUserIdOrderByIdDesc(Long userId);

    boolean existsByBookingIdAndType(Long bookingId, LoyaltyTxnType type);

    @Query("select coalesce(sum(t.points),0) from LoyaltyTransaction t where t.userId = :userId")
    int balance(@Param("userId") Long userId);

    @Query("select coalesce(sum(t.points),0) from LoyaltyTransaction t where t.userId = :userId and t.type = :type")
    int sumByType(@Param("userId") Long userId, @Param("type") LoyaltyTxnType type);

    /** So du KHA DUNG: tinh diem EARN con han (>= cutoff) + moi giao dich REDEEM/ADJUST (khong het han). */
    @Query("select coalesce(sum(t.points),0) from LoyaltyTransaction t "
            + "where t.userId = :userId and (t.type <> :earn or t.createdAt >= :cutoff)")
    int balanceValid(@Param("userId") Long userId, @Param("earn") LoyaltyTxnType earn, @Param("cutoff") Instant cutoff);

    /** Tong diem TICH con han (>= cutoff) - dung de xep hang. */
    @Query("select coalesce(sum(t.points),0) from LoyaltyTransaction t "
            + "where t.userId = :userId and t.type = :earn and t.createdAt >= :cutoff")
    int earnedValid(@Param("userId") Long userId, @Param("earn") LoyaltyTxnType earn, @Param("cutoff") Instant cutoff);

    /** Cac voucher khach da DOI tu diem (txn REDEEM co voucherCode). */
    List<LoyaltyTransaction> findByUserIdAndTypeAndVoucherCodeIsNotNullOrderByIdDesc(Long userId, LoyaltyTxnType type);
}
