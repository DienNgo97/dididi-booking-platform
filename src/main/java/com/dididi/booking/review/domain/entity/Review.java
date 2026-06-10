package com.dididi.booking.review.domain.entity;

import com.dididi.booking.booking.domain.enums.BookingType;
import com.dididi.booking.common.domain.BaseEntity;
import com.dididi.booking.review.domain.enums.ReviewStatus;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Danh gia cua khach cho mot don da CONFIRMED. Moi don chi 1 review (unique booking_id).
 * targetType + targetId tro toi khach san/chuyen bay de tinh rating trung binh.
 * status: kiem duyet (PENDING/PUBLISHED/HIDDEN). vendorReply: chu khach san tra loi.
 */
@Entity
@Table(name = "reviews",
        uniqueConstraints = @UniqueConstraint(name = "uk_reviews_booking", columnNames = "booking_id"))
public class Review extends BaseEntity {

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    private BookingType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(nullable = false)
    private int rating;

    @Column(length = 2000)
    private String comment;

    @Column(name = "reviewer_name", length = 150)
    private String reviewerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ReviewStatus status = ReviewStatus.PUBLISHED;

    @Column(name = "vendor_reply", length = 2000)
    private String vendorReply;

    @Column(name = "vendor_reply_at")
    private Instant vendorReplyAt;

    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public BookingType getTargetType() { return targetType; }
    public void setTargetType(BookingType targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }
    public ReviewStatus getStatus() { return status; }
    public void setStatus(ReviewStatus status) { this.status = status; }
    public String getVendorReply() { return vendorReply; }
    public void setVendorReply(String vendorReply) { this.vendorReply = vendorReply; }
    public Instant getVendorReplyAt() { return vendorReplyAt; }
    public void setVendorReplyAt(Instant vendorReplyAt) { this.vendorReplyAt = vendorReplyAt; }
}
