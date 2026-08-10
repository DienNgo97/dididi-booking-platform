package com.dididi.booking.voucher.repository;

import com.dididi.booking.voucher.domain.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {
    Optional<Voucher> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
    List<Voucher> findAllByOrderByIdDesc();

    /** Voucher RIÊNG của 1 khách (quà cá nhân hoá + voucher đổi điểm) — trang "Ưu đãi của tôi". */
    List<Voucher> findByOwnerUserIdOrderByIdDesc(Long ownerUserId);
}
