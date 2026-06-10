package com.dididi.booking.vendor.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.hotel.api.dto.HotelImageDto;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.hotel.service.HotelImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Vendor - Ảnh khách sạn của tôi", description = "Cần JWT role VENDOR. Quản ảnh gallery KS của mình.")
@RestController
@RequestMapping("/api/vendor/v1/my-hotel/images")
public class VendorHotelImageApiController {

    private final HotelRepository hotelRepository;
    private final HotelImageService hotelImageService;

    public VendorHotelImageApiController(HotelRepository hotelRepository, HotelImageService hotelImageService) {
        this.hotelRepository = hotelRepository;
        this.hotelImageService = hotelImageService;
    }

    private Hotel myHotel(Authentication auth) {
        if (auth == null) {
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        Long uid = Long.valueOf(auth.getName());
        return hotelRepository.findByVendorId(uid)
                .orElseThrow(() -> new BusinessException("NO_HOTEL",
                        "Tài khoản chưa gắn khách sạn nào", HttpStatus.NOT_FOUND));
    }

    @Operation(summary = "Danh sách ảnh KS của tôi")
    @GetMapping
    public ApiResponse<List<HotelImageDto>> list(Authentication auth) {
        return ApiResponse.ok(hotelImageService.listImages(myHotel(auth).getId()));
    }

    @Operation(summary = "Upload 1 ảnh cho KS của tôi (multipart field 'file')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<HotelImageDto> upload(Authentication auth, @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(hotelImageService.addImage(myHotel(auth).getId(), file), "Đã tải ảnh lên");
    }

    @Operation(summary = "Xoá 1 ảnh KS của tôi")
    @DeleteMapping("/{imageId}")
    public ApiResponse<Void> delete(Authentication auth, @PathVariable Long imageId) {
        hotelImageService.deleteImage(myHotel(auth).getId(), imageId);
        return ApiResponse.ok(null, "Đã xoá ảnh");
    }
}
