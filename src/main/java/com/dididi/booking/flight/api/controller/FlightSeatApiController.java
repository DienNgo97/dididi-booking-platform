package com.dididi.booking.flight.api.controller;

import com.dididi.booking.booking.FlightAddons;
import com.dididi.booking.booking.service.BookingService;
import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.flight.domain.entity.Flight;
import com.dididi.booking.flight.repository.FlightRepository;
import com.dididi.booking.integration.dto.SeatMapResult;
import com.dididi.booking.integration.service.MockFlightProviderAdapter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Sơ đồ ghế + danh mục suất ăn/hành lý cho vé máy bay (Flutter).
 * Chỉ chuyến bay của nhà cung cấp (externalId < 900000) mới có sơ đồ ghế.
 */
@Tag(name = "Flight seats & add-ons")
@RestController
@RequestMapping("/api/v1/flights")
public class FlightSeatApiController {

    private final BookingService bookingService;
    private final FlightRepository flightRepository;
    private final MockFlightProviderAdapter flightAdapter;

    public FlightSeatApiController(BookingService bookingService, FlightRepository flightRepository,
                                  MockFlightProviderAdapter flightAdapter) {
        this.bookingService = bookingService;
        this.flightRepository = flightRepository;
        this.flightAdapter = flightAdapter;
    }

    @Operation(summary = "Sơ đồ ghế của chuyến bay (null nếu chuyến không hỗ trợ chọn chỗ)")
    @GetMapping("/{id}/seatmap")
    public ApiResponse<SeatMapResult> seatmap(@PathVariable Long id) {
        Flight f = flightRepository.findById(id)
                .orElseThrow(() -> new BusinessException("FLIGHT_NOT_FOUND", "Không tìm thấy chuyến bay", HttpStatus.NOT_FOUND));
        if (!bookingService.isProviderFlight(f)) {
            return ApiResponse.ok(null); // chuyến nội bộ — không có sơ đồ ghế
        }
        return ApiResponse.ok(flightAdapter.getSeatMap(f.getExternalId()));
    }

    @Operation(summary = "Danh mục suất ăn & hành lý (giá là nguồn chân lý phía server)")
    @GetMapping("/addons")
    public ApiResponse<Map<String, Object>> addons() {
        return ApiResponse.ok(Map.of("meals", FlightAddons.MEALS, "bags", FlightAddons.BAGS));
    }
}
