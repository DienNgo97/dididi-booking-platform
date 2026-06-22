package com.dididi.booking.review.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.review.api.dto.ReviewImageDto;
import com.dididi.booking.review.domain.enums.ReviewImageKind;
import com.dididi.booking.review.service.ReviewImageService;
import com.dididi.booking.storage.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Tag(name = "Review images", description = "Ảnh đính kèm đánh giá của khách")
@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewImageApiController {

    private final ReviewImageService reviewImageService;

    public ReviewImageApiController(ReviewImageService reviewImageService) {
        this.reviewImageService = reviewImageService;
    }

    @Operation(summary = "Khách đính ảnh vào đánh giá của mình (multipart, field 'files')")
    @PostMapping(value = "/{reviewId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<List<ReviewImageDto>> upload(@PathVariable Long reviewId,
                                                    @RequestPart("files") MultipartFile[] files,
                                                    Authentication authentication) {
        if (authentication == null) {
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        Long userId = Long.valueOf(authentication.getName());
        return ApiResponse.ok(reviewImageService.attachReviewImages(reviewId, userId, files), "Đã tải ảnh lên");
    }

    @Operation(summary = "Danh sách ảnh của đánh giá (công khai): ảnh khách + ảnh phản hồi")
    @GetMapping("/{reviewId}/images")
    public ApiResponse<List<ReviewImageDto>> list(@PathVariable Long reviewId) {
        List<ReviewImageDto> all = new ArrayList<>(reviewImageService.listDtos(reviewId, ReviewImageKind.REVIEW));
        all.addAll(reviewImageService.listDtos(reviewId, ReviewImageKind.REPLY));
        return ApiResponse.ok(all);
    }

    @Operation(summary = "Tải/hiển thị 1 ảnh đánh giá (bytes) - dùng trong thẻ <img>")
    @GetMapping("/{reviewId}/images/{imageId}")
    public ResponseEntity<byte[]> serve(@PathVariable Long reviewId, @PathVariable Long imageId) {
        StorageService.StoredObject obj = reviewImageService.load(reviewId, imageId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(obj.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .body(obj.bytes());
    }
}
