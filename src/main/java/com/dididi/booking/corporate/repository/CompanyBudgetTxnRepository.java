package com.dididi.booking.corporate.repository;

import com.dididi.booking.corporate.domain.entity.CompanyBudgetTxn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanyBudgetTxnRepository extends JpaRepository<CompanyBudgetTxn, Long> {
    List<CompanyBudgetTxn> findByCompanyIdOrderByIdDesc(Long companyId);
    List<CompanyBudgetTxn> findByBookingIdOrderByIdDesc(Long bookingId);
}
