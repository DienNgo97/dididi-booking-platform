package com.dididi.booking.social.repository;

import com.dididi.booking.social.domain.entity.ContentReport;
import com.dididi.booking.social.domain.enums.ReactionTarget;
import com.dididi.booking.social.domain.enums.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContentReportRepository extends JpaRepository<ContentReport, Long> {

    boolean existsByReporterUserIdAndTargetTypeAndTargetId(Long reporterUserId, ReactionTarget targetType, Long targetId);

    long countByTargetTypeAndTargetIdAndStatus(ReactionTarget targetType, Long targetId, ReportStatus status);

    List<ContentReport> findByStatusOrderByIdDesc(ReportStatus status, Pageable pageable);

    // ===== ADMIN quản lý báo cáo =====

    /** Danh sách báo cáo theo trạng thái, phân trang (sort truyền qua Pageable). */
    Page<ContentReport> findByStatus(ReportStatus status, Pageable pageable);

    /** Đếm báo cáo theo trạng thái (cho dashboard). */
    long countByStatus(ReportStatus status);

    /** Mọi báo cáo (theo trạng thái) của cùng 1 đối tượng — để xử lý gộp. */
    List<ContentReport> findByTargetTypeAndTargetIdAndStatus(ReactionTarget targetType, Long targetId, ReportStatus status);
}
