package com.dididi.booking.review.api.dto;

import com.dididi.booking.review.domain.entity.Review;

import java.time.Instant;

public record ReviewDto(
        Long id,
        int rating,
        String comment,
        String reviewerName,
        String vendorReply,
        Instant vendorReplyAt,
        Instant createdAt) {

    public static ReviewDto from(Review r) {
        return new ReviewDto(r.getId(), r.getRating(), r.getComment(), r.getReviewerName(),
                r.getVendorReply(), r.getVendorReplyAt(), r.getCreatedAt());
    }
}
