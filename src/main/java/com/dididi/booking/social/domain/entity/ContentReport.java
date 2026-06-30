package com.dididi.booking.social.domain.entity;

import com.dididi.booking.common.domain.BaseEntity;
import com.dididi.booking.social.domain.enums.ReactionTarget;
import com.dididi.booking.social.domain.enums.ReportReason;
import com.dididi.booking.social.domain.enums.ReportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** Bao cao 1 bai/binh luan. targetType dung lai ReactionTarget (POST/COMMENT). */
@Entity
@Table(name = "social_reports", indexes = {
        @Index(name = "idx_report_status", columnList = "status,id"),
        @Index(name = "idx_report_target", columnList = "target_type,target_id")
})
public class ContentReport extends BaseEntity {

    @Column(name = "reporter_user_id", nullable = false)
    private Long reporterUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 8)
    private ReactionTarget targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ReportReason reason;

    @Column(length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReportStatus status = ReportStatus.OPEN;

    @Column(name = "handled_by_user_id")
    private Long handledByUserId;

    public Long getReporterUserId() { return reporterUserId; }
    public void setReporterUserId(Long reporterUserId) { this.reporterUserId = reporterUserId; }
    public ReactionTarget getTargetType() { return targetType; }
    public void setTargetType(ReactionTarget targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public ReportReason getReason() { return reason; }
    public void setReason(ReportReason reason) { this.reason = reason; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public ReportStatus getStatus() { return status; }
    public void setStatus(ReportStatus status) { this.status = status; }
    public Long getHandledByUserId() { return handledByUserId; }
    public void setHandledByUserId(Long handledByUserId) { this.handledByUserId = handledByUserId; }
}
