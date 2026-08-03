package com.dididi.booking.wishlist.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.hotel.api.dto.HotelApiDto;
import com.dididi.booking.wishlist.service.WishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** API danh sách yêu thích (wishlist) khách sạn cho khách (JWT: principal = userId). */
@Tag(name = "Wishlist (khách)")
@RestController
@RequestMapping("/api/v1/wishlist")
public class WishlistApiController {

    private final WishlistService wishlistService;

    public WishlistApiController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    private Long uid(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        return Long.valueOf(auth.getName());
    }

    @Operation(summary = "Danh sách khách sạn đã lưu")
    @GetMapping
    public ApiResponse<List<HotelApiDto>> list(Authentication auth) {
        // Map trực tiếp từ entity (HotelApiDto.from) — KHÔNG dùng hotelQueryService.findById (đang @Cacheable, có thể lỗi cache).
        List<HotelApiDto> out = wishlistService.listHotels(uid(auth)).stream()
                .map(HotelApiDto::from)
                .toList();
        return ApiResponse.ok(out);
    }

    @Operation(summary = "Kiểm tra 1 khách sạn có trong wishlist không")
    @GetMapping("/{hotelId}")
    public ApiResponse<Map<String, Object>> check(@PathVariable Long hotelId, Authentication auth) {
        return ApiResponse.ok(Map.of("wishlisted", wishlistService.isWishlisted(uid(auth), hotelId)));
    }

    @Operation(summary = "Thêm/bỏ khách sạn khỏi wishlist (toggle)")
    @PostMapping("/{hotelId}/toggle")
    public ApiResponse<Map<String, Object>> toggle(@PathVariable Long hotelId, Authentication auth) {
        return ApiResponse.ok(Map.of("wishlisted", wishlistService.toggle(uid(auth), hotelId)));
    }
}
