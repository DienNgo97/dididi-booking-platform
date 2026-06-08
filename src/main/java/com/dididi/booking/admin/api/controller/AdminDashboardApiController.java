package com.dididi.booking.admin.api.controller;

import com.dididi.booking.admin.api.dto.AdminBookingDto;
import com.dididi.booking.admin.api.dto.DashboardStatsDto;
import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.flight.repository.FlightRepository;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.identity.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Tag(name = "Admin - Dashboard", description = "Thống kê tổng quan. Cần JWT role admin")
@RestController
@RequestMapping("/api/admin/v1/dashboard")
public class AdminDashboardApiController {

    private final UserRepository userRepository;
    private final HotelRepository hotelRepository;
    private final FlightRepository flightRepository;
    private final BookingRepository bookingRepository;

    public AdminDashboardApiController(UserRepository userRepository,
                                       HotelRepository hotelRepository,
                                       FlightRepository flightRepository,
                                       BookingRepository bookingRepository) {
        this.userRepository = userRepository;
        this.hotelRepository = hotelRepository;
        this.flightRepository = flightRepository;
        this.bookingRepository = bookingRepository;
    }

    @Operation(summary = "Số liệu tổng quan + 5 đơn gần nhất")
    @GetMapping("/stats")
    public ApiResponse<DashboardStatsDto> stats() {
        // Doanh thu = tong amount cua cac don CONFIRMED (tinh trong Java cho an toan, quy mo do an nho)
        BigDecimal revenue = bookingRepository
                .findByStatus(BookingStatus.CONFIRMED, Pageable.unpaged())
                .getContent().stream()
                .map(Booking::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<AdminBookingDto> recent = bookingRepository.findTop5ByOrderByCreatedAtDesc()
                .stream().map(AdminBookingDto::from).toList();

        DashboardStatsDto dto = new DashboardStatsDto(
                userRepository.count(),
                hotelRepository.count(),
                flightRepository.count(),
                bookingRepository.count(),
                bookingRepository.countByStatus(BookingStatus.PENDING_PAYMENT),
                bookingRepository.countByStatus(BookingStatus.CONFIRMED),
                bookingRepository.countByStatus(BookingStatus.CANCELLED),
                bookingRepository.countByStatus(BookingStatus.FAILED),
                revenue,
                recent
        );
        return ApiResponse.ok(dto);
    }
}
