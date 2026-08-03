package com.dididi.booking.social.api.dto;

import com.dididi.booking.social.domain.entity.ContentReport;
import com.dididi.booking.social.domain.enums.ReactionTarget;
import com.dididi.booking.social.domain.enums.ReportReason;
import com.dididi.booking.social.domain.enums.ReportStatus;

import java.time.Instant;

/** Báo cáo nội dung cho màn kiểm duyệt admin. */
public record AdminSocialReportDto(
        Long id,
        ReactionTarget targetType,
        Long targetId,
        ReportReason reason,
        ReportStatus status,
        String note,
        Long reporterUserId,
        String reporterName,
        Long handledByUserId,
        Instant createdAt) {

    public static AdminSocialReportDto from(ContentReport r, String reporterName) {
        return new AdminSocialReportDto(
                r.getId(), r.getTargetType(), r.getTargetId(), r.getReason(), r.getStatus(),
                r.getNote(), r.getReporterUserId(), reporterName, r.getHandledByUserId(), r.getCreatedAt());
    }
}
