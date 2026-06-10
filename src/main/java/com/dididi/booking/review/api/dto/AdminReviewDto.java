package com.dididi.booking.review.api.dto;

import com.dididi.booking.booking.domain.enums.BookingType;
import com.dididi.booking.review.domain.entity.Review;
import com.dididi.booking.review.domain.enums.ReviewStatus;

import java.time.Instant;

public record AdminReviewDto(
        Long id,
        int rating,
        String comment,
        String reviewerName,
        ReviewStatus status,
        BookingType targetType,
        Long targetId,
        Long bookingId,
        String vendorReply,
        Instant vendorReplyAt,
        Instant createdAt) {

    public static AdminReviewDto from(Review r) {
        return new AdminReviewDto(r.getId(), r.getRating(), r.getComment(), r.getReviewerName(),
                r.getStatus(), r.getTargetType(), r.getTargetId(), r.getBookingId(),
                r.getVendorReply(), r.getVendorReplyAt(), r.getCreatedAt());
    }
}
