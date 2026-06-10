package com.dididi.booking.review.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body cho POST /api/v1/reviews. */
public record CreateReviewRequest(
        @NotBlank String bookingCode,
        @Min(1) @Max(5) int rating,
        @Size(max = 2000) String comment) {
}
