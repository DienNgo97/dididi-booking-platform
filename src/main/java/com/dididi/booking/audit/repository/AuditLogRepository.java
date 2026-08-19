package com.dididi.booking.audit.repository;

import com.dididi.booking.audit.domain.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** Tìm kiếm admin theo email người làm / hành động / chi tiết / loại đối tượng (thanh tìm kiếm tab Audit). */
    @org.springframework.data.jpa.repository.Query("""
            SELECT a FROM AuditLog a
            WHERE (lower(a.actorEmail) LIKE lower(concat('%', :q, '%'))
                   OR lower(a.action) LIKE lower(concat('%', :q, '%'))
                   OR lower(a.detail) LIKE lower(concat('%', :q, '%'))
                   OR lower(a.targetType) LIKE lower(concat('%', :q, '%')))
              AND (:action IS NULL OR a.action = :action)
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> adminSearch(@org.springframework.data.repository.query.Param("q") String q,
                               @org.springframework.data.repository.query.Param("action") String action,
                               Pageable pageable);

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<AuditLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);
}
