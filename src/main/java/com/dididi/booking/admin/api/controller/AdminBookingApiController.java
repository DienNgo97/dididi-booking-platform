package com.dididi.booking.admin.api.controller;

import com.dididi.booking.admin.api.dto.AdminBookingDto;
import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Bookings", description = "Cần JWT role ADMIN/SUPER_ADMIN/VENDOR")
@RestController
@RequestMapping("/api/admin/v1/bookings")
public class AdminBookingApiController {

    private final BookingRepository bookingRepository;

    public AdminBookingApiController(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Operation(summary = "Danh sách đơn (phân trang, lọc theo status tuỳ chọn)")
    @GetMapping
    public ApiResponse<PagedResponse<AdminBookingDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) BookingStatus status) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Booking> result = (status == null)
                ? bookingRepository.findAll(pageable)
                : bookingRepository.findByStatus(status, pageable);
        return ApiResponse.ok(PagedResponse.of(result.map(AdminBookingDto::from)));
    }

    @Operation(summary = "Chi tiết đơn theo id")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminBookingDto>> get(@PathVariable Long id) {
        return bookingRepository.findById(id)
                .map(b -> ResponseEntity.ok(ApiResponse.ok(AdminBookingDto.from(b))))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Huỷ đơn (admin) - chuyển status sang CANCELLED")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<AdminBookingDto>> cancel(@PathVariable Long id) {
        return bookingRepository.findById(id)
                .map(b -> {
                    b.setStatus(BookingStatus.CANCELLED);
                    bookingRepository.save(b);
                    return ResponseEntity.ok(ApiResponse.ok(AdminBookingDto.from(b), "Cancelled"));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
