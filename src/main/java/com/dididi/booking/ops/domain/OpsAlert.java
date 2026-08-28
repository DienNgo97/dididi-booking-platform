package com.dididi.booking.ops.domain;

import com.dididi.booking.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * CẢNH BÁO VẬN HÀNH (P0-3/P0-4, 28/08/2026) — "hỏng thì phải có người biết".
 *
 * Trước đây mọi job nền đều bắt lỗi rồi chỉ log: tiền lệch, ghế không xác nhận được, đơn kẹt...
 * đều chìm trong log tới khi khách khiếu nại. Bảng này là hộp thư nổi của hệ thống: watchdog phát
 * hiện bất thường thì mở một cảnh báo, admin thấy ngay trên tab "Cảnh báo" và tick xử lý.
 *
 * dedupeKey UNIQUE: cùng một vấn đề (vd cùng một đơn) chỉ tạo MỘT cảnh báo dù watchdog quét mỗi 5 phút
 * — chống ngập; khi vấn đề tự hết, watchdog tự đóng cảnh báo (RESOLVED, ghi rõ "tự khỏi").
 */
@Entity
@Table(name = "ops_alert",
        uniqueConstraints = @UniqueConstraint(name = "uk_ops_alert_dedupe", columnNames = "dedupe_key"),
        indexes = @Index(name = "idx_ops_alert_status", columnList = "status"))
public class OpsAlert extends BaseEntity {

    public enum Type {
        /** Payment đã PAID nhưng Booking không ở trạng thái CONFIRMED — khách mất tiền mà không có dịch vụ. */
        PAYMENT_BOOKING_MISMATCH,
        /** Đơn vé đã CONFIRMED nhưng chưa xác nhận được ghế với hãng — ghế có thể bị nhả cho người khác. */
        FLIGHT_SEAT_UNCONFIRMED,
        /** Đã ghi sổ hoàn tiền nhưng tiền chưa chuyển cho khách (P1-4) — việc của kế toán. */
        REFUND_PENDING_TRANSFER,
        /** Một job nền hỏng nhiều lần liên tiếp (P1-12) — trước đây chỉ log rồi thôi. */
        JOB_FAILING,
        /** Gọi giữ chỗ sang PMS bị timeout: bên kia có thể ĐÃ tạo phòng mà Dididi không có mã để huỷ. */
        PROVIDER_RESERVE_TIMEOUT
    }

    public enum Severity { CRITICAL, WARNING }

    public enum Status { OPEN, RESOLVED }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity = Severity.CRITICAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.OPEN;

    /** Khoá chống trùng, dạng "TYPE:bookingId". */
    @Column(name = "dedupe_key", nullable = false, length = 120)
    private String dedupeKey;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "booking_code", length = 40)
    private String bookingCode;

    @Column(nullable = false, length = 500)
    private String detail;

    /** Việc admin cần làm — viết bằng tiếng người, không bắt admin đoán. */
    @Column(name = "suggested_action", length = 400)
    private String suggestedAction;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolve_note", length = 400)
    private String resolveNote;

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getDedupeKey() { return dedupeKey; }
    public void setDedupeKey(String dedupeKey) { this.dedupeKey = dedupeKey; }
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public String getBookingCode() { return bookingCode; }
    public void setBookingCode(String bookingCode) { this.bookingCode = bookingCode; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getSuggestedAction() { return suggestedAction; }
    public void setSuggestedAction(String suggestedAction) { this.suggestedAction = suggestedAction; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public Long getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(Long resolvedBy) { this.resolvedBy = resolvedBy; }
    public String getResolveNote() { return resolveNote; }
    public void setResolveNote(String resolveNote) { this.resolveNote = resolveNote; }
}
