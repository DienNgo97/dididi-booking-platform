package com.dididi.booking.approval.repository;

import com.dididi.booking.approval.domain.ApprovalRequest;
import com.dididi.booking.approval.domain.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {
    List<ApprovalRequest> findByStatusOrderByIdDesc(ApprovalStatus status);
    List<ApprovalRequest> findByCompanyIdAndStatusOrderByIdDesc(Long companyId, ApprovalStatus status);
    Optional<ApprovalRequest> findFirstByBookingIdOrderByIdDesc(Long bookingId);
}
