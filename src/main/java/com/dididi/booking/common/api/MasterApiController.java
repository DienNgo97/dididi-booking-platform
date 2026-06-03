package com.dididi.booking.common.api;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.flight.repository.FlightRepository;
import com.dididi.booking.hotel.repository.HotelRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Tag(name = "Master data")
@RestController
@RequestMapping("/api/v1/master")
public class MasterApiController {

    private final HotelRepository hotelRepository;
    private final FlightRepository flightRepository;

    public MasterApiController(HotelRepository hotelRepository, FlightRepository flightRepository) {
        this.hotelRepository = hotelRepository;
        this.flightRepository = flightRepository;
    }

    @Operation(summary = "Danh sách thành phố (suy ra từ khách sạn)")
    @GetMapping("/cities")
    public ApiResponse<List<String>> cities() {
        Set<String> set = hotelRepository.findByActiveTrue().stream()
                .map(h -> h.getCity())
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.toCollection(TreeSet::new));
        return ApiResponse.ok(List.copyOf(set));
    }

    @Operation(summary = "Danh sách sân bay (suy ra từ chuyến bay)")
    @GetMapping("/airports")
    public ApiResponse<List<String>> airports() {
        Set<String> set = new TreeSet<>();
        flightRepository.findAll().forEach(f -> {
            if (f.getFromAirport() != null) set.add(f.getFromAirport());
            if (f.getToAirport() != null) set.add(f.getToAirport());
        });
        return ApiResponse.ok(List.copyOf(set));
    }
}
