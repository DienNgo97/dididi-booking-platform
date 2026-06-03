package com.dididi.booking.booking.api.controller;

import com.dididi.booking.booking.api.dto.BookingDto;
import com.dididi.booking.booking.api.dto.CreateBookingRequest;
import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.service.BookingService;
import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Bookings", description = "Đặt vé/phòng (cần Bearer token)")
@RestController
@RequestMapping("/api/v1/bookings")
public class BookingApiController {

    private final BookingService bookingService;
    private final PaymentService paymentService;

    public BookingApiController(BookingService bookingService, PaymentService paymentService) {
        this.bookingService = bookingService;
        this.paymentService = paymentService;
    }

    @Operation(summary = "Tạo đơn đặt (FLIGHT hoặc HOTEL), trạng thái PENDING_PAYMENT")
    @PostMapping
    public ApiResponse<BookingDto> create(@RequestBody CreateBookingRequest req, Authentication auth) {
        Long userId = userId(auth);
        Booking b;
        if ("FLIGHT".equalsIgnoreCase(req.type())) {
            b = bookingService.createFlightBooking(userId, req.flightId(), req.passengerName(),
                    req.contactEmail(), req.seats() == null ? 1 : req.seats());
        } else if ("HOTEL".equalsIgnoreCase(req.type())) {
            b = bookingService.createHotelBooking(userId, req.hotelId(), req.roomTypeId(), req.roomName(),
                    req.guestName(), req.checkIn(), req.checkOut(), req.rooms() == null ? 1 : req.rooms());
        } else {
            throw new BusinessException("BAD_TYPE", "type phải là FLIGHT hoặc HOTEL", HttpStatus.BAD_REQUEST);
        }
        return ApiResponse.ok(BookingDto.from(b), "Tạo đơn thành công");
    }

    @Operation(summary = "Danh sách đơn của tôi")
    @GetMapping("/me")
    public ApiResponse<List<BookingDto>> myBookings(Authentication auth) {
        return ApiResponse.ok(bookingService.myBookings(userId(auth)).stream().map(BookingDto::from).toList());
    }

    @Operation(summary = "Chi tiết đơn theo mã")
    @GetMapping("/{code}")
    public ApiResponse<BookingDto> get(@PathVariable String code, Authentication auth) {
        return ApiResponse.ok(BookingDto.from(bookingService.getForUser(code, userId(auth))));
    }

    @Operation(summary = "Huỷ đơn")
    @PostMapping("/{code}/cancel")
    public ApiResponse<BookingDto> cancel(@PathVariable String code, Authentication auth) {
        return ApiResponse.ok(BookingDto.from(bookingService.cancel(code, userId(auth))), "Đã huỷ");
    }

    @Operation(summary = "Thanh toán (giả lập) → xác nhận đơn")
    @PostMapping("/{code}/pay")
    public ApiResponse<BookingDto> pay(@PathVariable String code, Authentication auth) {
        Booking b = bookingService.getForUser(code, userId(auth));
        paymentService.pay(b);
        bookingService.markConfirmed(b);
        return ApiResponse.ok(BookingDto.from(b), "Thanh toán thành công");
    }

    private Long userId(Authentication auth) {
        if (auth == null) throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        return Long.valueOf(auth.getName());
    }
}
