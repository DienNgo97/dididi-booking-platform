package com.dididi.booking.admin.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.hotel.api.dto.HotelApiDto;
import com.dididi.booking.hotel.api.dto.HotelUpsertRequest;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.repository.HotelRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin - Hotels", description = "Cần JWT role ADMIN/SUPER_ADMIN/VENDOR")
@RestController
@RequestMapping("/api/admin/v1/hotels")
public class AdminHotelApiController {

    private final HotelRepository hotelRepository;

    public AdminHotelApiController(HotelRepository hotelRepository) {
        this.hotelRepository = hotelRepository;
    }

    @Operation(summary = "Danh sách tất cả khách sạn (admin)")
    @GetMapping
    public ApiResponse<List<HotelApiDto>> list() {
        return ApiResponse.ok(hotelRepository.findAll().stream().map(HotelApiDto::from).toList());
    }

    @Operation(summary = "Tạo khách sạn")
    @PostMapping
    public ApiResponse<HotelApiDto> create(@Valid @RequestBody HotelUpsertRequest req) {
        Hotel h = new Hotel();
        apply(h, req);
        hotelRepository.save(h);
        return ApiResponse.ok(HotelApiDto.from(h), "Created");
    }

    @Operation(summary = "Cập nhật khách sạn")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<HotelApiDto>> update(@PathVariable Long id,
                                                           @Valid @RequestBody HotelUpsertRequest req) {
        return hotelRepository.findById(id)
                .map(h -> {
                    apply(h, req);
                    hotelRepository.save(h);
                    return ResponseEntity.ok(ApiResponse.ok(HotelApiDto.from(h), "Updated"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Xoá khách sạn")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        if (!hotelRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        hotelRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Deleted"));
    }

    private void apply(Hotel h, HotelUpsertRequest req) {
        h.setName(req.name());
        h.setCity(req.city());
        h.setAddress(req.address());
        h.setDescription(req.description());
        h.setStarRating(req.starRating());
        h.setActive(req.active() == null || req.active());
    }
}
