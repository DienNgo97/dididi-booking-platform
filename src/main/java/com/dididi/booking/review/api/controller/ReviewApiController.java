package com.dididi.booking.review.api.controller;

import com.dididi.booking.booking.domain.enums.BookingType;
import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.dto.PagedResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.review.api.dto.CreateReviewRequest;
import com.dididi.booking.review.api.dto.ReviewDto;
import com.dididi.booking.review.api.dto.ReviewPageResponse;
import com.dididi.booking.review.domain.entity.Review;
import com.dididi.booking.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Reviews", description = "Đánh giá khách sạn / chuyến bay")
@RestController
@RequestMapping("/api/v1")
public class ReviewApiController {

    private final ReviewService reviewService;

    public ReviewApiController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(summary = "Đánh giá một đơn đã xác nhận (cần đăng nhập)")
    @PostMapping("/reviews")
    public ApiResponse<ReviewDto> create(@Valid @RequestBody CreateReviewRequest req,
                                         Authentication authentication) {
        if (authentication == null) {
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        Long userId = Long.valueOf(authentication.getName());
        Review r = reviewService.create(userId, req.bookingCode(), req.rating(), req.comment());
        return ApiResponse.ok(ReviewDto.from(r), "Cảm ơn bạn đã đánh giá");
    }

    @Operation(summary = "Đánh giá của một khách sạn (công khai)")
    @GetMapping("/hotels/{id}/reviews")
    public ApiResponse<ReviewPageResponse> hotelReviews(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(build(BookingType.HOTEL, id, page, size));
    }

    @Operation(summary = "Đánh giá của một chuyến bay (công khai)")
    @GetMapping("/flights/{id}/reviews")
    public ApiResponse<ReviewPageResponse> flightReviews(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(build(BookingType.FLIGHT, id, page, size));
    }

    private ReviewPageResponse build(BookingType type, Long targetId, int page, int size) {
        double avg = reviewService.averageRating(type, targetId);
        Page<ReviewDto> dtoPage = reviewService.list(type, targetId, page, size).map(ReviewDto::from);
        return new ReviewPageResponse(avg, PagedResponse.of(dtoPage));
    }
}
