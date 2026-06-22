package com.dididi.booking.admin.api.controller;

import com.dididi.booking.admin.api.dto.AdminBookingDto;
import com.dididi.booking.admin.api.dto.RefundRequest;
import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.domain.enums.CancelStatus;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.booking.service.BookingService;
import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.dto.PagedResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.common.security.RoleUtils;
import com.dididi.booking.payment.api.dto.RefundDto;
import com.dididi.booking.payment.domain.entity.Refund;
import com.dididi.booking.payment.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin - Bookings & Refunds", description = "Cần JWT role ADMIN/SUPER_ADMIN/VENDOR")
@RestController
@RequestMapping("/api/admin/v1/bookings")
public class AdminBookingApiController {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final RefundService refundService;

    public AdminBookingApiController(BookingRepository bookingRepository, BookingService bookingService,
                                     RefundService refundService) {
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.refundService = refundService;
    }

    @Operation(summary = "Danh sách đơn (phân trang, lọc theo status hoặc cancelStatus tuỳ chọn)")
    @GetMapping
    public ApiResponse<PagedResponse<AdminBookingDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) CancelStatus cancelStatus) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Booking> result;
        if (cancelStatus != null) {
            result = bookingRepository.findByCancelStatus(cancelStatus, pageable);
        } else if (status != null) {
            result = bookingRepository.findByStatus(status, pageable);
        } else {
            result = bookingRepository.findAll(pageable);
        }
        return ApiResponse.ok(PagedResponse.of(result.map(AdminBookingDto::from)));
    }

    @Operation(summary = "Lịch sử hoàn tiền")
    @GetMapping("/refunds")
    public ApiResponse<List<RefundDto>> refundHistory() {
        return ApiResponse.ok(refundService.history().stream().map(RefundDto::from).toList());
    }

    @Operation(summary = "Chi tiết đơn theo id")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminBookingDto>> get(@PathVariable Long id) {
        return bookingRepository.findById(id)
                .map(b -> ResponseEntity.ok(ApiResponse.ok(AdminBookingDto.from(b))))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Huỷ đơn (admin) - chuyển CANCELLED, hoàn trả tồn kho DIRECT")
    @Transactional
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<AdminBookingDto>> cancel(@PathVariable Long id) {
        return bookingRepository.findById(id)
                .map(b -> {
                    if (b.getStatus() == BookingStatus.PENDING_PAYMENT
                            || b.getStatus() == BookingStatus.CONFIRMED) {
                        bookingService.restoreDirectInventory(b);
                    }
                    b.setStatus(BookingStatus.CANCELLED);
                    bookingRepository.save(b);
                    return ResponseEntity.ok(ApiResponse.ok(AdminBookingDto.from(b), "Cancelled"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Hoàn tiền đơn (đơn phải CONFIRMED & đã thanh toán)")
    @PostMapping("/{id}/refund")
    public ResponseEntity<ApiResponse<AdminBookingDto>> refund(@PathVariable Long id,
                                                               @RequestBody(required = false) RefundRequest req,
                                                               Authentication auth) {
        Booking b = bookingRepository.findById(id).orElse(null);
        if (b == null) {
            return ResponseEntity.notFound().build();
        }
        Refund r = refundService.refund(b.getPublicCode(), userId(auth),
                req != null ? req.reason() : null, RoleUtils.isSuperAdmin(auth));
        Booking updated = bookingRepository.findById(id).orElse(b);
        return ResponseEntity.ok(ApiResponse.ok(AdminBookingDto.from(updated),
                "Đã hoàn tiền " + r.getAmount() + " " + r.getCurrency()));
    }

    @Operation(summary = "Duyệt yêu cầu huỷ của khách: hoàn tiền thực trả + chuyển CANCELLED")
    @Transactional
    @PostMapping("/{id}/cancel-request/approve")
    public ResponseEntity<ApiResponse<AdminBookingDto>> approveCancel(
            @PathVariable Long id, @RequestBody(required = false) RefundRequest req, Authentication auth) {
        Booking b = bookingRepository.findById(id).orElse(null);
        if (b == null) return ResponseEntity.notFound().build();
        if (b.getCancelStatus() != CancelStatus.REQUESTED) {
            throw new BusinessException("NO_CANCEL_REQUEST",
                    "Đơn không có yêu cầu huỷ đang chờ duyệt.", HttpStatus.BAD_REQUEST);
        }
        if (req == null || req.reason() == null || req.reason().isBlank()) {
            throw new BusinessException("REASON_REQUIRED",
                    "Vui lòng ghi lý do duyệt huỷ.", HttpStatus.BAD_REQUEST);
        }
        String note = req.reason().trim();
        // Hoan tien THUC TE da tra (Payment.amount = gia sau giam voucher) + chuyen CANCELLED + ghi audit.
        refundService.refund(b.getPublicCode(), userId(auth), note, RoleUtils.isSuperAdmin(auth));
        Booking after = bookingRepository.findById(id).orElse(b);
        after.setCancelStatus(CancelStatus.APPROVED);
        after.setCancelAdminNote(note);
        bookingRepository.save(after);
        return ResponseEntity.ok(ApiResponse.ok(AdminBookingDto.from(after), "Đã duyệt huỷ và hoàn tiền"));
    }

    @Operation(summary = "Từ chối yêu cầu huỷ của khách (đơn giữ nguyên, ghi lý do)")
    @Transactional
    @PostMapping("/{id}/cancel-request/reject")
    public ResponseEntity<ApiResponse<AdminBookingDto>> rejectCancel(
            @PathVariable Long id, @RequestBody(required = false) RefundRequest req) {
        Booking b = bookingRepository.findById(id).orElse(null);
        if (b == null) return ResponseEntity.notFound().build();
        if (b.getCancelStatus() != CancelStatus.REQUESTED) {
            throw new BusinessException("NO_CANCEL_REQUEST",
                    "Đơn không có yêu cầu huỷ đang chờ duyệt.", HttpStatus.BAD_REQUEST);
        }
        if (req == null || req.reason() == null || req.reason().isBlank()) {
            throw new BusinessException("REASON_REQUIRED",
                    "Vui lòng ghi lý do từ chối.", HttpStatus.BAD_REQUEST);
        }
        b.setCancelStatus(CancelStatus.REJECTED);
        b.setCancelAdminNote(req.reason().trim());
        bookingRepository.save(b);
        return ResponseEntity.ok(ApiResponse.ok(AdminBookingDto.from(b), "Đã từ chối yêu cầu huỷ"));
    }

    private Long userId(Authentication auth) {
        if (auth == null) throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        return Long.valueOf(auth.getName());
    }
}
