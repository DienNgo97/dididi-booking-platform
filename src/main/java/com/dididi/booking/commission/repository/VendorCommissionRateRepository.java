package com.dididi.booking.commission.repository;

import com.dididi.booking.commission.domain.VendorCommissionRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VendorCommissionRateRepository extends JpaRepository<VendorCommissionRate, Long> {
    Optional<VendorCommissionRate> findByVendorId(Long vendorId);
}
