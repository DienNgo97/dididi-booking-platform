package com.dididi.booking.flight.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.dto.PagedResponse;
import com.dididi.booking.flight.api.dto.FlightApiDto;
import com.dididi.booking.flight.service.FlightQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "Flights (public)")
@RestController
@RequestMapping("/api/v1/flights")
public class FlightApiController {

    private final FlightQueryService flightQueryService;

    public FlightApiController(FlightQueryService flightQueryService) {
        this.flightQueryService = flightQueryService;
    }

    @Operation(summary = "Tìm chuyến bay theo tuyến + ngày, có phân trang (chỉ chuyến chưa khởi hành)")
    @GetMapping
    public ApiResponse<PagedResponse<FlightApiDto>> search(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        page = Math.max(0, page);
        size = Math.min(Math.max(size, 1), 50);

        // GIỐNG WEB: chỉ hiện chuyến khởi hành sau hiện tại + 5 tiếng (đệm ra sân bay + boarding).
        // Lọc SAU cache (cache key không có thời gian) để không bao giờ trả chuyến đã bay.
        LocalDateTime cutoff = LocalDateTime.now().plusHours(5);
        List<FlightApiDto> all = flightQueryService.search(from, to, date).stream()
                .filter(f -> f.departureTime() != null && f.departureTime().isAfter(cutoff))
                .toList();

        int fromIdx = Math.min(page * size, all.size());
        int toIdx = Math.min(fromIdx + size, all.size());
        Page<FlightApiDto> p = new PageImpl<>(all.subList(fromIdx, toIdx), PageRequest.of(page, size), all.size());
        return ApiResponse.ok(PagedResponse.of(p));
    }

    @Operation(summary = "Chi tiết chuyến bay")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FlightApiDto>> get(@PathVariable Long id) {
        FlightApiDto dto = flightQueryService.findById(id);
        return dto == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(ApiResponse.ok(dto));
    }
}
