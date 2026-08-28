package com.dididi.booking.ops.service;

import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.ops.domain.OpsAlert;
import com.dididi.booking.ops.repository.OpsAlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/** Mở / đóng cảnh báo vận hành. Idempotent theo dedupeKey — quét lại nhiều lần không sinh rác. */
@Service
public class OpsAlertService {

    private static final Logger log = LoggerFactory.getLogger(OpsAlertService.class);

    private final OpsAlertRepository repository;

    public OpsAlertService(OpsAlertRepository repository) {
        this.repository = repository;
    }

    /**
     * Mở cảnh báo (hoặc cập nhật chi tiết nếu đang mở). REQUIRES_NEW: cảnh báo phải sống sót
     * kể cả khi transaction nghiệp vụ gọi nó bị rollback — đúng tinh thần "hỏng thì phải có người biết".
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void raise(OpsAlert.Type type, OpsAlert.Severity severity, Long bookingId, String bookingCode,
                      String detail, String suggestedAction) {
        String key = type.name() + ":" + (bookingId != null ? bookingId : bookingCode);
        try {
            OpsAlert a = repository.findByDedupeKey(key).orElseGet(OpsAlert::new);
            boolean isNew = a.getId() == null;
            a.setType(type);
            a.setSeverity(severity);
            a.setDedupeKey(key);
            a.setBookingId(bookingId);
            a.setBookingCode(bookingCode);
            a.setDetail(detail);
            a.setSuggestedAction(suggestedAction);
            if (isNew || a.getStatus() == OpsAlert.Status.RESOLVED) {
                // vấn đề tái phát sau khi đã đóng -> mở lại để admin không bỏ sót
                a.setStatus(OpsAlert.Status.OPEN);
                a.setResolvedAt(null);
                a.setResolvedBy(null);
                a.setResolveNote(null);
            }
            repository.save(a);
            if (isNew) {
                log.error("[ops] CẢNH BÁO MỚI [{}] {} — {}", severity, type, detail);
            }
        } catch (Exception e) {
            log.error("[ops] Không ghi được cảnh báo {} ({}): {}", type, key, e.toString());
        }
    }

    /** Vấn đề đã tự hết (vd: đơn được xác nhận lại) -> đóng cảnh báo, ghi rõ là tự khỏi. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoResolve(OpsAlert.Type type, Long bookingId, String note) {
        String key = type.name() + ":" + bookingId;
        repository.findByDedupeKey(key)
                .filter(a -> a.getStatus() == OpsAlert.Status.OPEN)
                .ifPresent(a -> {
                    a.setStatus(OpsAlert.Status.RESOLVED);
                    a.setResolvedAt(Instant.now());
                    a.setResolveNote(note);
                    repository.save(a);
                    log.info("[ops] Cảnh báo {} cho đơn #{} đã tự khỏi: {}", type, bookingId, note);
                });
    }

    @Transactional(readOnly = true)
    public Page<OpsAlert> list(OpsAlert.Status status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100));
        return (status == null)
                ? repository.findAllByOrderByStatusAscIdDesc(pageable)   // OPEN lên trước
                : repository.findByStatusOrderByIdDesc(status, pageable);
    }

    @Transactional(readOnly = true)
    public long openCount() {
        return repository.countByStatus(OpsAlert.Status.OPEN);
    }

    @Transactional
    public OpsAlert resolve(Long id, Long adminId, String note) {
        OpsAlert a = repository.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy cảnh báo", HttpStatus.NOT_FOUND));
        if (a.getStatus() == OpsAlert.Status.RESOLVED) {
            return a;                                       // bấm hai lần cũng không sao
        }
        a.setStatus(OpsAlert.Status.RESOLVED);
        a.setResolvedAt(Instant.now());
        a.setResolvedBy(adminId);
        a.setResolveNote(Optional.ofNullable(note).map(String::trim).filter(s -> !s.isEmpty())
                .orElse("Admin đánh dấu đã xử lý"));
        return repository.save(a);
    }
}
