package com.dididi.booking.flight.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.flight.api.dto.FlightApiDto;
import com.dididi.booking.flight.repository.FlightRepository;
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

    private final FlightRepository flightRepository;

    public FlightApiController(FlightRepository flightRepository) {
        this.flightRepository = flightRepository;
    }

    @Operation(summary = "Tìm chuyến bay theo tuyến + ngày")
    @GetMapping
    public ApiResponse<List<FlightApiDto>> search(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<FlightApiDto> result = flightRepository.findAllByOrderByDepartureTime().stream()
                .filter(f -> from == null || from.isBlank() || from.equalsIgnoreCase(f.getFromAirport()))
                .filter(f -> to == null || to.isBlank() || to.equalsIgnoreCase(f.getToAirport()))
                .filter(f -> date == null || (f.getDepartureTime() != null && f.getDepartureTime().toLocalDate().equals(date)))
                .map(FlightApiDto::from)
                .toList();
        return ApiResponse.ok(result);
    }

    @Operation(summary = "Chi tiết chuyến bay")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FlightApiDto>> get(@PathVariable Long id) {
        return flightRepository.findById(id)
                .map(f -> ResponseEntity.ok(ApiResponse.ok(FlightApiDto.from(f))))
                .orElse(ResponseEntity.notFound().build());
    }
}
