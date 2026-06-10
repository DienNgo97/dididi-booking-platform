package com.dididi.booking.review.api.dto;

import com.dididi.booking.common.dto.PagedResponse;

/** Tra ve cho GET .../reviews: diem trung binh + danh sach review (phan trang). */
public record ReviewPageResponse(
        double averageRating,
        PagedResponse<ReviewDto> reviews) {
}
