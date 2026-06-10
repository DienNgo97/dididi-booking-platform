package com.dididi.booking.vendor.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.dto.PagedResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.review.api.dto.AdminReviewDto;
import com.dididi.booking.review.api.dto.ReplyRequest;
import com.dididi.booking.review.api.dto.ReviewDto;
import com.dididi.booking.review.domain.entity.Review;
import com.dididi.booking.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Vendor - Trả lời đánh giá", description = "Cần JWT role VENDOR. Chỉ trả lời đánh giá trên khách sạn của mình.")
@RestController
@RequestMapping("/api/vendor/v1/reviews")
public class VendorReviewApiController {

    private final ReviewService reviewService;

    public VendorReviewApiController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(summary = "Danh sách đánh giá trên khách sạn của vendor (mọi trạng thái)")
    @GetMapping
    public ApiResponse<PagedResponse<AdminReviewDto>> myHotelReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        if (authentication == null) {
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        Long vendorUserId = Long.valueOf(authentication.getName());
        Page<AdminReviewDto> p = reviewService.listForVendor(vendorUserId, page, size).map(AdminReviewDto::from);
        return ApiResponse.ok(PagedResponse.of(p));
    }

    @Operation(summary = "Trả lời một đánh giá trên khách sạn của vendor")
    @PostMapping("/{id}/reply")
    public ApiResponse<ReviewDto> reply(@PathVariable Long id,
                                        @Valid @RequestBody ReplyRequest req,
                                        Authentication authentication) {
        if (authentication == null) {
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        Long vendorUserId = Long.valueOf(authentication.getName());
        Review r = reviewService.vendorReply(id, vendorUserId, req.reply());
        return ApiResponse.ok(ReviewDto.from(r), "Đã trả lời đánh giá");
    }
}
