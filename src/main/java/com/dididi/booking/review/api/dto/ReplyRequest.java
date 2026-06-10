package com.dididi.booking.review.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body cho vendor tra loi review. */
public record ReplyRequest(
        @NotBlank @Size(max = 2000) String reply) {
}
