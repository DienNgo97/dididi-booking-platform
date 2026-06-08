package com.dididi.booking.admin.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.dto.PagedResponse;
import com.dididi.booking.flight.api.dto.FlightApiDto;
import com.dididi.booking.flight.domain.entity.Flight;
import com.dididi.booking.flight.repository.FlightRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Flights", description = "Read-only (đồng bộ từ flight-provider). Cần JWT role admin")
@RestController
@RequestMapping("/api/admin/v1/flights")
public class AdminFlightApiController {

    private final FlightRepository flightRepository;

    public AdminFlightApiController(FlightRepository flightRepository) {
        this.flightRepository = flightRepository;
    }

    @Operation(summary = "Danh sách chuyến bay đã đồng bộ (phân trang)")
    @GetMapping
    public ApiResponse<PagedResponse<FlightApiDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "departureTime"));
        Page<Flight> result = flightRepository.findAll(pageable);
        return ApiResponse.ok(PagedResponse.of(result.map(FlightApiDto::from)));
    }

    @Operation(summary = "Chi tiết chuyến bay theo id")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FlightApiDto>> get(@PathVariable Long id) {
        return flightRepository.findById(id)
                .map(f -> ResponseEntity.ok(ApiResponse.ok(FlightApiDto.from(f))))
                .orElse(ResponseEntity.notFound().build());
    }
}
