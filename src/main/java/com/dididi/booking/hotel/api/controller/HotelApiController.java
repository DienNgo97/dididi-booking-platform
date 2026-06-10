package com.dididi.booking.hotel.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.dto.PagedResponse;
import com.dididi.booking.hotel.api.dto.HotelApiDto;
import com.dididi.booking.hotel.service.HotelQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Hotels (public)")
@RestController
@RequestMapping("/api/v1/hotels")
public class HotelApiController {

    private final HotelQueryService hotelQueryService;

    public HotelApiController(HotelQueryService hotelQueryService) {
        this.hotelQueryService = hotelQueryService;
    }

    @Operation(summary = "Danh sách khách sạn, lọc theo thành phố, có phân trang")
    @GetMapping
    public ApiResponse<PagedResponse<HotelApiDto>> list(
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        String normalizedCity = (city == null || city.isBlank()) ? null : city.trim();
        List<HotelApiDto> all = hotelQueryService.listActive(normalizedCity);

        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        List<HotelApiDto> pageContent = all.subList(from, to);
        Page<HotelApiDto> p = new PageImpl<>(pageContent, PageRequest.of(page, size), all.size());
        return ApiResponse.ok(PagedResponse.of(p));
    }

    @Operation(summary = "Chi tiết khách sạn")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<HotelApiDto>> get(@PathVariable Long id) {
        HotelApiDto dto = hotelQueryService.findById(id);
        return dto == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(ApiResponse.ok(dto));
    }
}
