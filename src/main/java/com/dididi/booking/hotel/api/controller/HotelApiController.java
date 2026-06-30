package com.dididi.booking.hotel.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.dto.PagedResponse;
import com.dididi.booking.hotel.api.dto.HotelApiDto;
import com.dididi.booking.hotel.api.dto.HotelMapDto;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.hotel.service.HotelQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@Tag(name = "Hotels (public)")
@RestController
@RequestMapping("/api/v1/hotels")
public class HotelApiController {

    private final HotelQueryService hotelQueryService;
    private final HotelRepository hotelRepository;

    public HotelApiController(HotelQueryService hotelQueryService, HotelRepository hotelRepository) {
        this.hotelQueryService = hotelQueryService;
        this.hotelRepository = hotelRepository;
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

    @Operation(summary = "Khách sạn (có toạ độ) để vẽ marker trên bản đồ; lọc theo khung nhìn nếu truyền bounds")
    @GetMapping("/map")
    public ApiResponse<List<HotelMapDto>> map(@RequestParam(required = false) Double north,
                                              @RequestParam(required = false) Double south,
                                              @RequestParam(required = false) Double east,
                                              @RequestParam(required = false) Double west) {
        List<Hotel> hotels;
        if (north != null && south != null && east != null && west != null) {
            hotels = hotelRepository.findByActiveTrueAndLatBetweenAndLngBetween(
                    Math.min(south, north), Math.max(south, north),
                    Math.min(west, east), Math.max(west, east));
        } else {
            hotels = hotelRepository.findByActiveTrueAndLatIsNotNullAndLngIsNotNull();
        }
        return ApiResponse.ok(hotels.stream().map(HotelMapDto::from).toList());
    }

    @Operation(summary = "Khách sạn gần một vị trí (lat,lng) trong bán kính km, sắp theo khoảng cách tăng dần")
    @GetMapping("/nearby")
    public ApiResponse<List<HotelMapDto>> nearby(@RequestParam double lat,
                                                 @RequestParam double lng,
                                                 @RequestParam(defaultValue = "5") double radiusKm) {
        double latD = radiusKm / 111.0;
        double lngD = radiusKm / (111.0 * Math.max(0.2, Math.cos(Math.toRadians(lat))));
        List<Hotel> box = hotelRepository.findByActiveTrueAndLatBetweenAndLngBetween(
                lat - latD, lat + latD, lng - lngD, lng + lngD);
        List<HotelMapDto> out = box.stream()
                .filter(h -> haversineKm(lat, lng, h.getLat(), h.getLng()) <= radiusKm)
                .sorted(Comparator.comparingDouble(h -> haversineKm(lat, lng, h.getLat(), h.getLng())))
                .map(HotelMapDto::from)
                .toList();
        return ApiResponse.ok(out);
    }

    @Operation(summary = "Chi tiết khách sạn")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<HotelApiDto>> get(@PathVariable Long id) {
        HotelApiDto dto = hotelQueryService.findById(id);
        return dto == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(ApiResponse.ok(dto));
    }

    /** Khoảng cách Haversine giữa 2 toạ độ (km). */
    private static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
