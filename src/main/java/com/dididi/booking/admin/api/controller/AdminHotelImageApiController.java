package com.dididi.booking.admin.api.controller;

import com.dididi.booking.audit.event.AuditEvent;
import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.hotel.api.dto.HotelImageDto;
import com.dididi.booking.hotel.service.HotelImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Admin - Ảnh khách sạn", description = "ADMIN/SUPER_ADMIN quản ảnh gallery cho bất kỳ khách sạn nào.")
@RestController
@RequestMapping("/api/admin/v1/hotels/{hotelId}/images")
public class AdminHotelImageApiController {

    private final HotelImageService hotelImageService;
    private final ApplicationEventPublisher events;

    public AdminHotelImageApiController(HotelImageService hotelImageService, ApplicationEventPublisher events) {
        this.hotelImageService = hotelImageService;
        this.events = events;
    }

    private static Long actorId(Authentication auth) {
        try { return auth == null ? null : Long.valueOf(auth.getName()); } catch (Exception e) { return null; }
    }

    @Operation(summary = "Danh sách ảnh của khách sạn")
    @GetMapping
    public ApiResponse<List<HotelImageDto>> list(@PathVariable Long hotelId) {
        return ApiResponse.ok(hotelImageService.listImages(hotelId));
    }

    @Operation(summary = "Upload 1 ảnh cho khách sạn (multipart field 'file')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<HotelImageDto> upload(@PathVariable Long hotelId, @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(hotelImageService.addImage(hotelId, file), "Đã tải ảnh lên");
    }

    @Operation(summary = "Xoá 1 ảnh của khách sạn")
    @DeleteMapping("/{imageId}")
    public ApiResponse<Void> delete(@PathVariable Long hotelId, @PathVariable Long imageId, Authentication auth) {
        hotelImageService.deleteImage(hotelId, imageId);
        events.publishEvent(new AuditEvent(actorId(auth), "DELETE_HOTEL_IMAGE", "HOTEL", hotelId,
                "Xoá ảnh #" + imageId + " của KS #" + hotelId));
        return ApiResponse.ok(null, "Đã xoá ảnh");
    }
}
