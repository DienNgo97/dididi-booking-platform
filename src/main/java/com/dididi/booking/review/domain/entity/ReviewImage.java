package com.dididi.booking.review.domain.entity;

import com.dididi.booking.common.domain.BaseEntity;
import com.dididi.booking.review.domain.enums.ReviewImageKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Anh dinh kem 1 review: cua khach (REVIEW) hoac cua phan hoi vendor (REPLY). Luu tren MinIO. */
@Entity
@Table(name = "review_image")
public class ReviewImage extends BaseEntity {

    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private ReviewImageKind kind;

    @Column(name = "object_key", nullable = false, length = 300)
    private String objectKey;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public Long getReviewId() { return reviewId; }
    public void setReviewId(Long reviewId) { this.reviewId = reviewId; }
    public ReviewImageKind getKind() { return kind; }
    public void setKind(ReviewImageKind kind) { this.kind = kind; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
