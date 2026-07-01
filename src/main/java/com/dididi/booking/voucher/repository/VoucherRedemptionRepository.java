package com.dididi.booking.voucher.repository;

import com.dididi.booking.voucher.domain.VoucherRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** BP-VOU-02: kho ban ghi su dung voucher (unique (voucher_code, user_id)). */
public interface VoucherRedemptionRepository extends JpaRepository<VoucherRedemption, Long> {

    long countByVoucherCode(String voucherCode);

    boolean existsByVoucherCodeAndUserId(String voucherCode, Long userId);

    Optional<VoucherRedemption> findByVoucherCodeAndUserId(String voucherCode, Long userId);

    Optional<VoucherRedemption> findByBookingId(Long bookingId);
}
