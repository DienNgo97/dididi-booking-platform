package com.dididi.booking.social.repository;

import com.dididi.booking.social.domain.entity.ContentReport;
import com.dididi.booking.social.domain.enums.ReactionTarget;
import com.dididi.booking.social.domain.enums.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContentReportRepository extends JpaRepository<ContentReport, Long> {

    /** Tìm kiếm admin: ghi chú / lý do / loại đối tượng, kèm lọc status (thanh tìm kiếm tab Báo cáo cộng đồng). */
    @org.springframework.data.jpa.repository.Query("""
            SELECT r FROM ContentReport r
            WHERE (lower(r.note) LIKE lower(concat('%', :q, '%'))
                   OR lower(str(r.reason)) LIKE lower(concat('%', :q, '%'))
                   OR lower(str(r.targetType)) LIKE lower(concat('%', :q, '%')))
              AND (:status IS NULL OR r.status = :status)
            ORDER BY r.id DESC
            """)
    org.springframework.data.domain.Page<ContentReport> adminSearch(
            @org.springframework.data.repository.query.Param("q") String q,
            @org.springframework.data.repository.query.Param("status") com.dididi.booking.social.domain.enums.ReportStatus status,
            org.springframework.data.domain.Pageable pageable);

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
