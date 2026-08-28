package com.dididi.booking.commission.domain;

import com.dididi.booking.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * P1-9 — LỊCH SỬ TỶ LỆ HOA HỒNG, có NGÀY HIỆU LỰC.
 *
 * <p>Trước đây đối soát lấy tỷ lệ "hiện tại" và nhân cho toàn bộ doanh thu của kỳ. Admin đổi tỷ lệ
 * giữa tháng là công nợ của những ngày ĐÃ QUA cũng đổi theo — đối tác có cơ sở tranh chấp vì số
 * họ tự tính theo hợp đồng không khớp số Dididi đưa ra.</p>
 *
 * <p>Nay mỗi lần đổi ghi một dòng với {@code effectiveFrom} = ngày đầu kỳ KẾ TIẾP: tỷ lệ mới chỉ
 * áp cho kỳ chưa bắt đầu, kỳ đang chạy và kỳ đã qua giữ nguyên tỷ lệ cũ.</p>
 */
@Entity
@Table(name = "commission_rate_history")
public class CommissionRateHistory extends BaseEntity {

    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal rate;

    /** Ngày bắt đầu áp dụng (luôn là ngày 1 của một tháng). */
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "changed_by")
    private Long changedBy;

    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public Long getChangedBy() { return changedBy; }
    public void setChangedBy(Long changedBy) { this.changedBy = changedBy; }
}
