package com.dididi.booking.review.api.dto;

import com.dididi.booking.review.domain.entity.ReviewImage;

/** url = endpoint app phuc vu anh (cong khai), khong lo objectKey MinIO ra ngoai. */
public record ReviewImageDto(Long id, Long reviewId, String url) {

    public static ReviewImageDto from(ReviewImage i) {
        return new ReviewImageDto(i.getId(), i.getReviewId(),
                "/api/v1/reviews/" + i.getReviewId() + "/images/" + i.getId());
    }
}
