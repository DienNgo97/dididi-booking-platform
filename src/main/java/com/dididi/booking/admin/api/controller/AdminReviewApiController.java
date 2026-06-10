package com.dididi.booking.admin.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.dto.PagedResponse;
import com.dididi.booking.review.api.dto.AdminReviewDto;
import com.dididi.booking.review.domain.enums.ReviewStatus;
import com.dididi.booking.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Kiểm duyệt đánh giá", description = "Cần JWT role ADMIN/SUPER_ADMIN.")
@RestController
@RequestMapping("/api/admin/v1/reviews")
public class AdminReviewApiController {

    private final ReviewService reviewService;

    public AdminReviewApiController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(summary = "Danh sách đánh giá (lọc theo trạng thái nếu truyền ?status=)")
    @GetMapping
    public ApiResponse<PagedResponse<AdminReviewDto>> list(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AdminReviewDto> p = reviewService.listForAdmin(status, page, size).map(AdminReviewDto::from);
        return ApiResponse.ok(PagedResponse.of(p));
    }

    @Operation(summary = "Hiện / duyệt một đánh giá (PUBLISHED)")
    @PostMapping("/{id}/publish")
    public ApiResponse<AdminReviewDto> publish(@PathVariable Long id) {
        return ApiResponse.ok(AdminReviewDto.from(reviewService.setStatus(id, ReviewStatus.PUBLISHED)), "Đã hiện");
    }

    @Operation(summary = "Ẩn một đánh giá (HIDDEN)")
    @PostMapping("/{id}/hide")
    public ApiResponse<AdminReviewDto> hide(@PathVariable Long id) {
        return ApiResponse.ok(AdminReviewDto.from(reviewService.setStatus(id, ReviewStatus.HIDDEN)), "Đã ẩn");
    }

    @Operation(summary = "Xoá hẳn một đánh giá")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        reviewService.delete(id);
        return ApiResponse.ok(null, "Đã xoá");
    }
}
