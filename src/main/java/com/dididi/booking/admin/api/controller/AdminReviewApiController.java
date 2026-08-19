package com.dididi.booking.admin.api.controller;

import com.dididi.booking.audit.event.AuditEvent;
import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.dto.PagedResponse;
import com.dididi.booking.review.api.dto.AdminReviewDto;
import com.dididi.booking.review.domain.enums.ReviewStatus;
import com.dididi.booking.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Kiểm duyệt đánh giá", description = "Cần JWT role ADMIN/SUPER_ADMIN.")
@RestController
@RequestMapping("/api/admin/v1/reviews")
public class AdminReviewApiController {

    private final ReviewService reviewService;
    private final ApplicationEventPublisher events;

    public AdminReviewApiController(ReviewService reviewService, ApplicationEventPublisher events) {
        this.reviewService = reviewService;
        this.events = events;
    }

    private static Long actorId(Authentication auth) {
        try { return auth == null ? null : Long.valueOf(auth.getName()); } catch (Exception e) { return null; }
    }

    @Operation(summary = "Danh sách đánh giá (lọc theo trạng thái nếu truyền ?status=)")
    @GetMapping
    public ApiResponse<PagedResponse<AdminReviewDto>> list(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AdminReviewDto> p = reviewService.listForAdmin(status, q, page, size).map(AdminReviewDto::from);
        return ApiResponse.ok(PagedResponse.of(p));
    }

    @Operation(summary = "Hiện / duyệt một đánh giá (PUBLISHED)")
    @PostMapping("/{id}/publish")
    public ApiResponse<AdminReviewDto> publish(@PathVariable Long id) {
        return ApiResponse.ok(AdminReviewDto.from(reviewService.setStatus(id, ReviewStatus.PUBLISHED)), "Đã hiện");
    }

    @Operation(summary = "Ẩn một đánh giá (HIDDEN)")
    @PostMapping("/{id}/hide")
    public ApiResponse<AdminReviewDto> hide(@PathVariable Long id, Authentication auth) {
        AdminReviewDto dto = AdminReviewDto.from(reviewService.setStatus(id, ReviewStatus.HIDDEN));
        events.publishEvent(new AuditEvent(actorId(auth), "HIDE_REVIEW", "REVIEW", id, "Ẩn đánh giá"));
        return ApiResponse.ok(dto, "Đã ẩn");
    }

    @Operation(summary = "Xoá hẳn một đánh giá")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication auth) {
        reviewService.delete(id);
        events.publishEvent(new AuditEvent(actorId(auth), "DELETE_REVIEW", "REVIEW", id, "Xoá đánh giá"));
        return ApiResponse.ok(null, "Đã xoá");
    }
}
