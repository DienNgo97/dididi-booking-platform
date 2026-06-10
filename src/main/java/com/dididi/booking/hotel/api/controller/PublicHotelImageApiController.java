package com.dididi.booking.hotel.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.hotel.api.dto.HotelImageDto;
import com.dididi.booking.hotel.service.HotelImageService;
import com.dididi.booking.storage.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@Tag(name = "Public - Ảnh khách sạn", description = "Công khai: liệt kê + phục vụ ảnh gallery.")
@RestController
@RequestMapping("/api/v1/hotels")
public class PublicHotelImageApiController {

    private final HotelImageService hotelImageService;

    public PublicHotelImageApiController(HotelImageService hotelImageService) {
        this.hotelImageService = hotelImageService;
    }

    @Operation(summary = "Danh sách ảnh của khách sạn")
    @GetMapping("/{hotelId}/images")
    public ApiResponse<List<HotelImageDto>> list(@PathVariable Long hotelId) {
        return ApiResponse.ok(hotelImageService.listImages(hotelId));
    }

    @Operation(summary = "Tải/hiển thị 1 ảnh (bytes) - dùng trong thẻ <img>")
    @GetMapping("/{hotelId}/images/{imageId}")
    public ResponseEntity<byte[]> serve(@PathVariable Long hotelId, @PathVariable Long imageId) {
        StorageService.StoredObject obj = hotelImageService.loadImage(hotelId, imageId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(obj.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .body(obj.bytes());
    }
}
