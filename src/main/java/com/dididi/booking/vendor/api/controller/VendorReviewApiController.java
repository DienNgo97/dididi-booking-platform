package com.dididi.booking.vendor.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.dto.PagedResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.review.api.dto.AdminReviewDto;
import com.dididi.booking.review.api.dto.ReviewDto;
import com.dididi.booking.review.domain.entity.Review;
import com.dididi.booking.review.domain.enums.ReviewImageKind;
import com.dididi.booking.review.service.ReviewImageService;
import com.dididi.booking.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Vendor - Trả lời đánh giá", description = "Cần JWT role VENDOR. Chỉ trả lời đánh giá trên khách sạn của mình.")
@RestController
@RequestMapping("/api/vendor/v1/reviews")
public class VendorReviewApiController {

    private final ReviewService reviewService;
    private final ReviewImageService reviewImageService;

    public VendorReviewApiController(ReviewService reviewService, ReviewImageService reviewImageService) {
        this.reviewService = reviewService;
        this.reviewImageService = reviewImageService;
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
        Page<AdminReviewDto> p = reviewService.listForVendor(vendorUserId, page, size)
                .map(r -> AdminReviewDto.from(r,
                        reviewImageService.listUrls(r.getId(), ReviewImageKind.REVIEW),
                        reviewImageService.listUrls(r.getId(), ReviewImageKind.REPLY)));
        return ApiResponse.ok(PagedResponse.of(p));
    }

    @Operation(summary = "Trả lời một đánh giá trên khách sạn của vendor (kèm ảnh tuỳ chọn)")
    @PostMapping("/{id}/reply")
    public ApiResponse<ReviewDto> reply(@PathVariable Long id,
                                        @RequestParam("reply") String reply,
                                        @RequestParam(value = "images", required = false) MultipartFile[] images,
                                        Authentication authentication) {
        if (authentication == null) {
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        if (reply == null || reply.isBlank()) {
            throw new BusinessException("EMPTY_REPLY", "Nội dung phản hồi không được để trống", HttpStatus.BAD_REQUEST);
        }
        if (reply.length() > 2000) {
            throw new BusinessException("REPLY_TOO_LONG", "Phản hồi tối đa 2000 ký tự", HttpStatus.BAD_REQUEST);
        }
        Long vendorUserId = Long.valueOf(authentication.getName());
        Review r = reviewService.vendorReply(id, vendorUserId, reply);
        if (images != null && images.length > 0) {
            reviewImageService.attachReplyImages(id, vendorUserId, images);
        }
        return ApiResponse.ok(ReviewDto.from(r,
                reviewImageService.listUrls(id, ReviewImageKind.REVIEW),
                reviewImageService.listUrls(id, ReviewImageKind.REPLY)), "Đã trả lời đánh giá");
    }
}
