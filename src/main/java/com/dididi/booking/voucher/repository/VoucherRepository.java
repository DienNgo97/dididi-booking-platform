package com.dididi.booking.voucher.repository;

import com.dididi.booking.voucher.domain.Voucher;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    /**
     * P1-13 — khoá dòng voucher để đếm-rồi-ghi lượt dùng thành thao tác tuần tự.
     * Không có nó, hai khách bấm cùng lúc đều đếm được "còn suất" rồi cùng ghi ⇒ mã flash-sale
     * bị dùng vượt số lượng đã dự trù ngân sách.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Voucher v where v.id = :id")
    Optional<Voucher> findByIdForUpdate(@Param("id") Long id);

    Optional<Voucher> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
    List<Voucher> findAllByOrderByIdDesc();

    /** Voucher RIÊNG của 1 khách (quà cá nhân hoá + voucher đổi điểm) — trang "Ưu đãi của tôi". */
    List<Voucher> findByOwnerUserIdOrderByIdDesc(Long ownerUserId);
}
