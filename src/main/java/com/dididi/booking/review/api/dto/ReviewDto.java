package com.dididi.booking.review.api.dto;

import com.dididi.booking.review.domain.entity.Review;

import java.time.Instant;
import java.util.List;

public record ReviewDto(
        Long id,
        int rating,
        String comment,
        String reviewerName,
        String vendorReply,
        Instant vendorReplyAt,
        Instant createdAt,
        List<String> images,
        List<String> replyImages) {

    public static ReviewDto from(Review r) {
        return from(r, List.of(), List.of());
    }

    public static ReviewDto from(Review r, List<String> images, List<String> replyImages) {
        return new ReviewDto(r.getId(), r.getRating(), r.getComment(), r.getReviewerName(),
                r.getVendorReply(), r.getVendorReplyAt(), r.getCreatedAt(),
                images != null ? images : List.of(),
                replyImages != null ? replyImages : List.of());
    }
}
