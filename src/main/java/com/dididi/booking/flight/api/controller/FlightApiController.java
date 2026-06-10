package com.dididi.booking.flight.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.flight.api.dto.FlightApiDto;
import com.dididi.booking.flight.service.FlightQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Flights (public)")
@RestController
@RequestMapping("/api/v1/flights")
public class FlightApiController {

    private final FlightQueryService flightQueryService;

    public FlightApiController(FlightQueryService flightQueryService) {
        this.flightQueryService = flightQueryService;
    }

    @Operation(summary = "Tìm chuyến bay theo tuyến + ngày")
    @GetMapping
    public ApiResponse<List<FlightApiDto>> search(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(flightQueryService.search(from, to, date));
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
