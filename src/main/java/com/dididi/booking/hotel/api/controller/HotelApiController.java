package com.dididi.booking.hotel.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.dto.PagedResponse;
import com.dididi.booking.hotel.api.dto.HotelApiDto;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.repository.HotelRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Hotels (public)")
@RestController
@RequestMapping("/api/v1/hotels")
public class HotelApiController {

    private final HotelRepository hotelRepository;

    public HotelApiController(HotelRepository hotelRepository) {
        this.hotelRepository = hotelRepository;
    }

    @Operation(summary = "Danh sách khách sạn, lọc theo thành phố, có phân trang")
    @GetMapping
    public ApiResponse<PagedResponse<HotelApiDto>> list(
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        List<Hotel> all = (city == null || city.isBlank())
                ? hotelRepository.findByActiveTrue()
                : hotelRepository.findByActiveTrueAndCityContainingIgnoreCase(city);

        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        List<HotelApiDto> pageContent = all.subList(from, to).stream().map(HotelApiDto::from).toList();
        Page<HotelApiDto> p = new org.springframework.data.domain.PageImpl<>(
                pageContent, PageRequest.of(page, size), all.size());
        return ApiResponse.ok(PagedResponse.of(p));
    }

    @Operation(summary = "Chi tiết khách sạn")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<HotelApiDto>> get(@PathVariable Long id) {
        return hotelRepository.findById(id)
                .map(h -> ResponseEntity.ok(ApiResponse.ok(HotelApiDto.from(h))))
                .orElse(ResponseEntity.notFound().build());
    }
}
