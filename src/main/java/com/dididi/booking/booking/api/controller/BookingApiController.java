package com.dididi.booking.booking.api.controller;

import com.dididi.booking.booking.FlightAddons;
import com.dididi.booking.booking.api.dto.BookingDto;
import com.dididi.booking.booking.api.dto.CreateBookingRequest;
import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.service.BookingService;
import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.corporate.service.CorporateBookingService;
import com.dididi.booking.corporate.service.CorporatePaymentOutcome;
import com.dididi.booking.invoice.service.InvoiceService;
import com.dididi.booking.payment.service.PaymentService;
import com.dididi.booking.payment.vnpay.VnPayService;
import com.dididi.booking.voucher.service.VoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Tag(name = "Bookings", description = "Đặt vé/phòng (cần Bearer token)")
@RestController
@RequestMapping("/api/v1/bookings")
public class BookingApiController {

    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final VnPayService vnPayService;
    private final VoucherService voucherService;
    private final InvoiceService invoiceService;
    private final CorporateBookingService corporateBookingService;

    public BookingApiController(BookingService bookingService, PaymentService paymentService,
                               VnPayService vnPayService, VoucherService voucherService,
                               InvoiceService invoiceService, CorporateBookingService corporateBookingService) {
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        this.vnPayService = vnPayService;
        this.voucherService = voucherService;
        this.invoiceService = invoiceService;
        this.corporateBookingService = corporateBookingService;
    }

    @Operation(summary = "Tạo đơn đặt (FLIGHT hoặc HOTEL), trạng thái PENDING_PAYMENT")
    @PostMapping
    public ApiResponse<BookingDto> create(@RequestBody CreateBookingRequest req, Authentication auth) {
        Long userId = userId(auth);
        Booking b;
        if ("FLIGHT".equalsIgnoreCase(req.type())) {
            b = bookingService.createFlightBooking(userId, req.flightId(), req.passengerName(),
                    req.contactEmail(), req.seats() == null ? 1 : req.seats(), null, java.math.BigDecimal.ZERO);
        } else if ("HOTEL".equalsIgnoreCase(req.type())) {
            int rooms = req.rooms() == null ? 1 : req.rooms();
            if (Boolean.TRUE.equals(req.dayUse())) {
                b = bookingService.createDayUseHotelBooking(userId, req.hotelId(), req.roomTypeId(), req.roomName(),
                        req.guestName(), req.checkIn(), req.checkInTime(), req.checkOutTime(), rooms);
            } else {
                b = bookingService.createHotelBooking(userId, req.hotelId(), req.roomTypeId(), req.roomName(),
                        req.guestName(), req.checkIn(), req.checkOut(), rooms);
            }
        } else {
            throw new BusinessException("BAD_TYPE", "type phải là FLIGHT hoặc HOTEL", HttpStatus.BAD_REQUEST);
        }
        return ApiResponse.ok(BookingDto.from(b), "Tạo đơn thành công");
    }

    @Operation(summary = "Đặt vé: thông tin từng hành khách (tên + suất ăn + hành lý), có/không sơ đồ ghế; phụ phí tính lại phía server")
    @PostMapping("/flight-seats")
    @SuppressWarnings("unchecked")
    public ApiResponse<BookingDto> flightSeats(@RequestBody Map<String, Object> body, Authentication auth) {
        Long uid = userId(auth);
        Long flightId = Long.valueOf(body.get("flightId").toString());
        String contactEmail = body.get("contactEmail") == null ? "" : body.get("contactEmail").toString();
        List<String> seatCodes = new ArrayList<>();
        if (body.get("seatCodes") instanceof List<?> l) {
            for (Object o : l) if (o != null) seatCodes.add(o.toString());
        }

        String passengerName;
        long extras;
        String passengersText;
        int seatCount;

        Object paxObj = body.get("passengers");
        if (paxObj instanceof List<?> paxList && !paxList.isEmpty()) {
            // Gom thông tin TỪNG hành khách + tính phụ phí ở server (giống web /booking/flight).
            StringBuilder pax = new StringBuilder();
            long ex = 0;
            String firstName = null;
            int i = 0;
            for (Object o : paxList) {
                Map<String, Object> m = (o instanceof Map) ? (Map<String, Object>) o : Map.of();
                String name = m.get("name") == null ? "" : m.get("name").toString().trim();
                if (name.isEmpty()) { i++; continue; }
                if (firstName == null) firstName = name;
                String mealCode = m.get("meal") == null ? null : m.get("meal").toString();
                String bagCode = m.get("bag") == null ? null : m.get("bag").toString();
                ex += FlightAddons.mealPrice(mealCode) + FlightAddons.bagPrice(bagCode);
                if (pax.length() > 0) pax.append("\n");
                pax.append("• ").append(name);
                if (i < seatCodes.size()) pax.append(" · ghế ").append(seatCodes.get(i));
                pax.append(" · 🍽 ").append(FlightAddons.mealLabel(mealCode));
                pax.append(" · 🧳 ").append(FlightAddons.bagLabel(bagCode));
                i++;
            }
            passengerName = firstName != null ? firstName : "Khách";
            extras = ex;
            passengersText = pax.length() > 0 ? pax.toString() : passengerName;
            seatCount = !seatCodes.isEmpty() ? seatCodes.size() : Math.max(1, i);
        } else {
            // Tương thích cũ: 1 tên + 1 suất ăn + 1 hành lý cho toàn bộ.
            passengerName = body.get("passengerName") == null ? "Khách" : body.get("passengerName").toString();
            String meal = body.get("meal") == null ? null : body.get("meal").toString();
            String bag = body.get("bag") == null ? null : body.get("bag").toString();
            extras = FlightAddons.mealPrice(meal) + FlightAddons.bagPrice(bag);
            passengersText = passengerName
                    + (meal != null ? " · " + FlightAddons.mealLabel(meal) : "")
                    + (bag != null ? " · " + FlightAddons.bagLabel(bag) : "");
            seatCount = !seatCodes.isEmpty() ? seatCodes.size() : 1;
        }

        Booking b = seatCodes.isEmpty()
                ? bookingService.createFlightBooking(uid, flightId, passengerName, contactEmail,
                        seatCount, passengersText, BigDecimal.valueOf(extras))
                : bookingService.createFlightBookingWithSeats(uid, flightId, passengerName, contactEmail,
                        seatCodes, passengersText, BigDecimal.valueOf(extras));
        return ApiResponse.ok(BookingDto.from(b), "Đã đặt vé");
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
    public ApiResponse<BookingDto> cancel(@PathVariable String code,
                                          @RequestParam(required = false) String reason,
                                          Authentication auth) {
        return ApiResponse.ok(BookingDto.from(bookingService.requestCancel(code, userId(auth), reason)),
                "Đã gửi yêu cầu huỷ, chờ admin duyệt");
    }

    @Operation(summary = "Thanh toán (giả lập) → xác nhận đơn")
    @PostMapping("/{code}/pay")
    public ApiResponse<BookingDto> pay(@PathVariable String code, Authentication auth) {
        Booking b = bookingService.getForUser(code, userId(auth));
        paymentService.pay(b);
        bookingService.markConfirmed(b);
        return ApiResponse.ok(BookingDto.from(b), "Thanh toán thành công");
    }

    @Operation(summary = "Tạo URL thanh toán VNPay cho đơn (mở trên trình duyệt/app)")
    @PostMapping("/{code}/vnpay-url")
    public ApiResponse<Map<String, String>> vnpayUrl(@PathVariable String code, Authentication auth,
                                                     HttpServletRequest req) {
        Booking b = bookingService.getForUser(code, userId(auth));
        // Guard giống luồng web (PaymentWebController): chỉ đơn đang chờ thanh toán, chưa hết hạn giữ chỗ.
        if (b.getStatus() != com.dididi.booking.booking.domain.enums.BookingStatus.PENDING_PAYMENT) {
            throw new BusinessException("BOOKING_NOT_PAYABLE",
                    "Đơn không còn ở trạng thái chờ thanh toán", HttpStatus.CONFLICT);
        }
        if (bookingService.isPaymentExpired(b)) {
            bookingService.markPaymentExpired(b);
            throw new BusinessException("PAYMENT_EXPIRED",
                    "Thời gian thanh toán đã hết hạn, vui lòng đặt lại", HttpStatus.CONFLICT);
        }
        // BẮT BUỘC tạo bản ghi Payment PENDING trước (initiateVnpay) để return/IPN tra được txnRef
        // và đối chiếu số tiền — nếu không, khách trả tiền xong đơn KHÔNG bao giờ được xác nhận.
        com.dididi.booking.payment.domain.entity.Payment p = paymentService.initiateVnpay(b);
        String url = vnPayService.createPaymentUrl(b, p.getTransactionRef(), clientIp(req));
        return ApiResponse.ok(Map.of("payUrl", url));
    }

    @Operation(summary = "Sửa đơn KS trực tiếp (đổi ngày/giờ/số phòng) khi còn PENDING_PAYMENT")
    @PostMapping("/{code}/edit")
    public ApiResponse<BookingDto> edit(@PathVariable String code, @RequestBody Map<String, String> body,
                                        Authentication auth) {
        Long uid = userId(auth);
        int rooms = body.get("rooms") != null ? Integer.parseInt(body.get("rooms")) : 1;
        boolean dayUse = "true".equalsIgnoreCase(body.getOrDefault("dayUse", "false"));
        Booking b;
        if (dayUse) {
            b = bookingService.editDirectDayUse(code, uid,
                    LocalDate.parse(body.get("date")),
                    LocalTime.parse(body.get("timeIn")),
                    LocalTime.parse(body.get("timeOut")), rooms);
        } else {
            b = bookingService.editDirectOvernight(code, uid,
                    LocalDate.parse(body.get("checkIn")),
                    LocalDate.parse(body.get("checkOut")), rooms);
        }
        return ApiResponse.ok(BookingDto.from(b), "Đã cập nhật đơn");
    }

    @Operation(summary = "Áp mã giảm giá (voucher) cho đơn")
    @PostMapping("/{code}/voucher")
    public ApiResponse<BookingDto> applyVoucher(@PathVariable String code, @RequestBody Map<String, String> body,
                                                Authentication auth) {
        Long uid = userId(auth);
        Booking b = bookingService.getForUser(code, uid);
        b = voucherService.apply(body.getOrDefault("voucherCode", ""), b, uid);
        return ApiResponse.ok(BookingDto.from(b), "Đã áp dụng mã giảm giá");
    }

    @Operation(summary = "Gỡ mã giảm giá khỏi đơn")
    @PostMapping("/{code}/voucher/remove")
    public ApiResponse<BookingDto> removeVoucher(@PathVariable String code, Authentication auth) {
        Booking b = bookingService.getForUser(code, userId(auth));
        b = voucherService.remove(b);
        return ApiResponse.ok(BookingDto.from(b), "Đã gỡ mã giảm giá");
    }

    @Operation(summary = "Thanh toán bằng ngân sách công ty (B2B) — có thể chuyển sang chờ duyệt")
    @PostMapping("/{code}/pay-company")
    public ApiResponse<Map<String, Object>> payCompany(@PathVariable String code, Authentication auth) {
        Long uid = userId(auth);
        CorporatePaymentOutcome outcome = corporateBookingService.payWithCompanyBudget(code, uid);
        Booking b = bookingService.getForUser(code, uid);
        String msg = outcome == CorporatePaymentOutcome.CONFIRMED
                ? "Đã thanh toán bằng ngân sách công ty" : "Đã gửi yêu cầu duyệt chi công ty";
        return ApiResponse.ok(Map.of("outcome", outcome.name(), "booking", BookingDto.from(b)), msg);
    }

    @Operation(summary = "Tải hoá đơn VAT (PDF) của đơn đã xác nhận")
    @GetMapping("/{code}/invoice")
    public ResponseEntity<byte[]> invoice(@PathVariable String code, Authentication auth) {
        bookingService.getForUser(code, userId(auth)); // kiểm quyền sở hữu + tồn tại (chống IDOR)
        byte[] pdf = invoiceService.generateForBooking(code);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"hoa-don-" + code + ".pdf\"")
                .body(pdf);
    }

    private static String clientIp(HttpServletRequest req) {
        String xf = req.getHeader("X-Forwarded-For");
        return (xf != null && !xf.isBlank()) ? xf.split(",")[0].trim() : req.getRemoteAddr();
    }

    private Long userId(Authentication auth) {
        if (auth == null) throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        return Long.valueOf(auth.getName());
    }
}
