package com.dididi.booking.audit.service;

import com.dididi.booking.audit.domain.entity.AuditLog;
import com.dididi.booking.audit.repository.AuditLogRepository;
import com.dididi.booking.identity.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    /** Ghi 1 dong nhat ky. Tham gia transaction cua loi goi (atomic voi hanh dong). */
    public void log(Long actorUserId, String action, String targetType, Long targetId, String detail) {
        AuditLog a = new AuditLog();
        a.setActorUserId(actorUserId);
        if (actorUserId != null) {
            userRepository.findById(actorUserId).ifPresent(u -> a.setActorEmail(u.getEmail()));
        }
        a.setAction(action);
        a.setTargetType(targetType);
        a.setTargetId(targetId);
        a.setDetail(detail);
        auditLogRepository.save(a);
    }

    public Page<AuditLog> list(String action, int page, int size) {
        return list(action, null, page, size);
    }

    /** Bản có tìm kiếm (thanh tìm kiếm tab Audit): q khớp email / hành động / chi tiết / loại đối tượng. */
    public Page<AuditLog> list(String action, String q, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        if (q != null && !q.isBlank()) {
            return auditLogRepository.adminSearch(q.trim(), (action == null || action.isBlank()) ? null : action, pageable);
        }
        return (action == null || action.isBlank())
                ? auditLogRepository.findAllByOrderByCreatedAtDesc(pageable)
                : auditLogRepository.findByActionOrderByCreatedAtDesc(action, pageable);
    }
}
