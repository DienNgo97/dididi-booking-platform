package com.dididi.booking.wallet.repository;

import com.dididi.booking.wallet.domain.entity.PayoutRequest;
import com.dididi.booking.wallet.domain.enums.PayoutStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface PayoutRequestRepository extends JpaRepository<PayoutRequest, Long> {

    Page<PayoutRequest> findByVendorIdOrderByIdDesc(Long vendorId, Pageable pageable);

    Page<PayoutRequest> findAllByOrderByIdDesc(Pageable pageable);

    Page<PayoutRequest> findByStatusOrderByIdDesc(PayoutStatus status, Pageable pageable);

    /** Tổng tiền đang GIỮ CHỖ (yêu cầu chưa chốt) — trừ khỏi khả dụng khi tính toán. */
    @Query("select coalesce(sum(p.amount), 0) from PayoutRequest p " +
            "where p.vendorId = :vendorId and p.status in :holding")
    BigDecimal holdingAmount(@Param("vendorId") Long vendorId,
                             @Param("holding") Collection<PayoutStatus> holding);

    List<PayoutRequest> findByStatusOrderByIdAsc(PayoutStatus status);

    /** Đổi trạng thái NGUYÊN TỬ: chỉ thành công khi đang đúng trạng thái 'from' (chống race huỷ vs xử lý). */
    @org.springframework.data.jpa.repository.Modifying
    @Query("update PayoutRequest p set p.status = :to where p.id = :id and p.status = :from")
    int transition(@Param("id") Long id, @Param("from") PayoutStatus from, @Param("to") PayoutStatus to);

    /** Yêu cầu kẹt PROCESSING quá lâu (app restart giữa chừng) — scheduler nhặt xử lý lại. */
    List<PayoutRequest> findByStatusAndUpdatedAtBefore(PayoutStatus status, Instant before);
}
