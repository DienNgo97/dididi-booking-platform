package com.dididi.booking.admin.api.controller;

import com.dididi.booking.admin.api.dto.AdminBookingDto;
import com.dididi.booking.admin.api.dto.RefundRequest;
import com.dididi.booking.audit.event.AuditEvent;
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
import org.springframework.context.ApplicationEventPublisher;
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
    private final com.dididi.booking.notification.service.UserNotificationService userNotificationService;
    private final ApplicationEventPublisher events;
    private final com.dididi.booking.voucher.service.VoucherService voucherService;

    public AdminBookingApiController(BookingRepository bookingRepository, BookingService bookingService,
                                     RefundService refundService,
                                     com.dididi.booking.notification.service.UserNotificationService userNotificationService,
                                     ApplicationEventPublisher events,
                                     com.dididi.booking.voucher.service.VoucherService voucherService) {
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.refundService = refundService;
        this.userNotificationService = userNotificationService;
        this.events = events;
        this.voucherService = voucherService;
    }

    @Operation(summary = "Danh sách đơn (phân trang, lọc theo status hoặc cancelStatus tuỳ chọn)")
    @GetMapping
    public ApiResponse<PagedResponse<AdminBookingDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) CancelStatus cancelStatus,
            @RequestParam(required = false) String q) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Booking> result;
        if (q != null && !q.isBlank()) {
            // Thanh tìm kiếm tab Đơn đặt: mã đơn / tiêu đề, vẫn tôn trọng lọc status/cancelStatus.
            result = bookingRepository.adminSearch(q.trim(), status, cancelStatus, pageable);
        } else if (cancelStatus != null) {
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

    @Operation(summary = "Khoản hoàn ĐÃ ghi sổ nhưng chưa chuyển tiền (việc của kế toán)")
    @GetMapping("/refunds/pending-transfer")
    public ApiResponse<List<RefundDto>> pendingTransfers() {
        return ApiResponse.ok(refundService.pendingTransfers().stream().map(RefundDto::from).toList());
    }

    public record TransferRequest(String transactionRef) {}

    @Operation(summary = "Xác nhận ĐÃ chuyển tiền hoàn cho khách (kèm mã giao dịch)")
    @PostMapping("/refunds/{refundId}/transferred")
    public ApiResponse<RefundDto> markTransferred(@PathVariable Long refundId,
                                                  @RequestBody(required = false) TransferRequest req,
                                                  Authentication auth) {
        Long adminId = auth == null ? null : Long.valueOf(auth.getName());
        return ApiResponse.ok(RefundDto.from(
                refundService.markTransferred(refundId, adminId, req == null ? null : req.transactionRef())),
                "Đã ghi nhận chuyển tiền cho khách");
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
    public ResponseEntity<ApiResponse<AdminBookingDto>> cancel(@PathVariable Long id, Authentication auth) {
        return bookingRepository.findById(id)
                .map(b -> {
                    // P0-2 (28/08): KHÔNG cho huỷ trắng đơn ĐÃ THANH TOÁN. Trước đây nhánh này nhận cả
                    // CONFIRMED: đơn bị huỷ, trả phòng/ghế/voucher nhưng KHÔNG hoàn tiền khách, KHÔNG đảo
                    // điểm, KHÔNG trả ngân sách công ty, Payment vẫn PAID — và không cứu được nữa vì
                    // RefundService chỉ nhận đơn CONFIRMED. Nặng hơn từ khi có ví vendor: đơn CANCELLED
                    // sẽ bị ghi bút toán ĐẢO, vendor mất doanh thu trong khi khách chưa nhận lại tiền.
                    // Đơn đã thanh toán BẮT BUỘC đi đường hoàn tiền (POST /{id}/refund) để đủ 4 bước.
                    if (b.getStatus() == BookingStatus.CONFIRMED) {
                        throw new BusinessException("USE_REFUND_INSTEAD",
                                com.dididi.booking.common.i18n.I18nSupport.msg("err.USE_REFUND_INSTEAD",
                                        "Đơn đã thanh toán — hãy dùng chức năng Hoàn tiền để huỷ (hoàn tiền khách, "
                                        + "đảo điểm, trả ngân sách công ty). Huỷ trắng sẽ làm mất tiền của khách."),
                                HttpStatus.CONFLICT);
                    }
                    if (b.getStatus() == BookingStatus.PENDING_PAYMENT) {
                        bookingService.restoreDirectInventory(b);
                        bookingService.releaseProviderInventory(b);   // INT-01: tra ghe/phong ve provider
                    }
                    b.setStatus(BookingStatus.CANCELLED);
                    bookingRepository.save(b);
                    voucherService.releaseForBooking(b.getId());   // BP-VOU-03: trả voucher để khách dùng lại
                    Long actorId = null;
                    try { if (auth != null) actorId = Long.valueOf(auth.getName()); } catch (Exception ignored) { }
                    events.publishEvent(new AuditEvent(actorId, "CANCEL_BOOKING", "BOOKING", b.getId(),
                            "Admin huỷ đơn " + b.getPublicCode()));
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
        try {
            userNotificationService.create(b.getUserId(),
                    com.dididi.booking.notification.domain.UserNotificationType.BOOKING_CANCEL_REJECTED,
                    "Yêu cầu huỷ bị từ chối",
                    "Yêu cầu huỷ đơn " + b.getPublicCode() + " không được duyệt: " + req.reason().trim(),
                    "/account/bookings", b.getId());
        } catch (Exception ignored) { }
        return ResponseEntity.ok(ApiResponse.ok(AdminBookingDto.from(b), "Đã từ chối yêu cầu huỷ"));
    }

    private Long userId(Authentication auth) {
        if (auth == null) throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        return Long.valueOf(auth.getName());
    }
}
