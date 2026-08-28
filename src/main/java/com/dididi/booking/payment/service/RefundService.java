package com.dididi.booking.payment.service;

import com.dididi.booking.audit.event.AuditEvent;
import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.booking.service.BookingService;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.loyalty.domain.LoyaltyTransaction;
import com.dididi.booking.loyalty.domain.LoyaltyTxnType;
import com.dididi.booking.loyalty.repository.LoyaltyTransactionRepository;
import com.dididi.booking.notification.EmailService;
import com.dididi.booking.payment.domain.entity.Payment;
import com.dididi.booking.payment.domain.entity.Refund;
import com.dididi.booking.payment.domain.enums.PaymentStatus;
import com.dididi.booking.payment.domain.enums.RefundStatus;
import com.dididi.booking.payment.repository.PaymentRepository;
import com.dididi.booking.payment.repository.RefundRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Hoan tien (Phase 8b) do ADMIN/SUPER_ADMIN chu dong thuc hien.
 * Nguong duyet (Dot2 Super Admin): khoan >= app.refund.super-admin-threshold can SUPER_ADMIN.
 * Hoan NOI BO: ghi nhan refund + dao Payment->REFUNDED, Booking->CANCELLED, hoan ton kho DIRECT,
 * ghi audit log. Goi API refund VNPay that = nang cao (chua bat).
 */
@Service
public class RefundService {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final LoyaltyTransactionRepository loyaltyRepository;
    private final EmailService emailService;
    private final ApplicationEventPublisher events;
    private final com.dididi.booking.notification.service.UserNotificationService userNotificationService;
    private final com.dididi.booking.corporate.service.CompanyService companyService;
    private final com.dididi.booking.voucher.service.VoucherService voucherService;
    private final BigDecimal superAdminThreshold;

    // BP-PAY-02: default khop policy (5.000.000 VND ~ don lon thuong can SUPER_ADMIN duyet) va trung
    // y nghia voi gia tri trong application.yml. Neu key bi xoa, default nay van la mot nguong HOP LY
    // (khong tut ve 0 lam moi refund can SUPER_ADMIN, cung khong nhay len 10tr lam mat hieu luc gate).
    // application.yml.app.refund.super-admin-threshold la nguon su that duy nhat; default chi la fallback an toan.
    public RefundService(BookingRepository bookingRepository, BookingService bookingService,
                         PaymentRepository paymentRepository, RefundRepository refundRepository,
                         LoyaltyTransactionRepository loyaltyRepository,
                         EmailService emailService, ApplicationEventPublisher events,
                         com.dididi.booking.notification.service.UserNotificationService userNotificationService,
                         com.dididi.booking.corporate.service.CompanyService companyService,
                         com.dididi.booking.voucher.service.VoucherService voucherService,
                         @Value("${app.refund.super-admin-threshold:5000000}") BigDecimal superAdminThreshold) {
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.loyaltyRepository = loyaltyRepository;
        this.emailService = emailService;
        this.events = events;
        this.userNotificationService = userNotificationService;
        this.companyService = companyService;
        this.voucherService = voucherService;
        this.superAdminThreshold = superAdminThreshold;
    }

    @Transactional
    public Refund refund(String publicCode, Long adminUserId, String reason, boolean isSuperAdmin) {
        Booking b = bookingRepository.findByPublicCode(publicCode)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy đơn", HttpStatus.NOT_FOUND));

        if (b.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException("CANNOT_REFUND",
                    "Chỉ hoàn tiền được đơn đã xác nhận (CONFIRMED). Trạng thái hiện tại: " + b.getStatus(),
                    HttpStatus.CONFLICT);
        }
        // CHÍNH SÁCH CỬA SỔ KHIẾU NẠI (VW4, chốt với Jay 19/08): quá checkOut + 3 ngày thì KHÔNG còn
        // hoàn tiền — kể cả SUPER_ADMIN. Đây là điểm thắt DUY NHẤT của mọi đường hoàn (duyệt huỷ cũng
        // đi qua đây), và là bất biến giữ cho ví vendor không bao giờ âm: tiền chỉ trở thành "khả dụng
        // rút" (VendorWalletService) đúng lúc quyền hoàn tiền hết hạn.
        // NGOẠI LỆ duy nhất: đơn đang có yêu cầu huỷ TREO (CancelStatus.REQUESTED — vốn chỉ nộp được
        // từ trước nhận phòng 48h, tức luôn TRONG hạn) -> admin xử lý muộn vẫn hoàn được, khách không
        // bị thiệt vì admin chậm; tiền tương ứng đã bị ví GIỮ LẠI nên không thể bị rút trước.
        boolean pendingComplaint = b.getCancelStatus() == com.dididi.booking.booking.domain.enums.CancelStatus.REQUESTED;
        java.time.LocalDate refundDeadline = (b.getCheckOut() != null)
                ? b.getCheckOut().plusDays(com.dididi.booking.wallet.service.VendorWalletService.COMPLAINT_WINDOW_DAYS)
                : null;
        if (!pendingComplaint && refundDeadline != null && java.time.LocalDate.now().isAfter(refundDeadline)) {
            throw new BusinessException("REFUND_WINDOW_CLOSED",
                    com.dididi.booking.common.i18n.I18nSupport.msg("err.REFUND_WINDOW_CLOSED",
                            "Đã quá thời hạn hoàn tiền ({0}, tức 3 ngày sau trả phòng không có khiếu nại). Doanh thu đơn này đã được chuyển cho đối tác.",
                            refundDeadline.toString()),
                    HttpStatus.CONFLICT);
        }
        // Nguong duyet: khoan >= threshold can SUPER_ADMIN.
        if (b.getAmount() != null && b.getAmount().compareTo(superAdminThreshold) >= 0 && !isSuperAdmin) {
            throw new BusinessException("NEEDS_SUPER_ADMIN",
                    "Khoản hoàn từ " + superAdminThreshold.toBigInteger() + " trở lên cần Super Admin duyệt",
                    HttpStatus.FORBIDDEN);
        }
        Payment p = paymentRepository.findByBookingId(b.getId())
                .orElseThrow(() -> new BusinessException("NO_PAYMENT",
                        "Đơn chưa có thanh toán để hoàn", HttpStatus.CONFLICT));
        if (p.getStatus() != PaymentStatus.PAID) {
            throw new BusinessException("NOT_PAID",
                    "Thanh toán chưa ở trạng thái PAID (hiện tại: " + p.getStatus() + ")", HttpStatus.CONFLICT);
        }

        // 1) Ghi nhan lan hoan tien (toan phan).
        Refund r = new Refund();
        r.setBookingId(b.getId());
        r.setPaymentId(p.getId());
        r.setAmount(p.getAmount());
        r.setCurrency(p.getCurrency());
        r.setReason(reason);
        r.setStatus(RefundStatus.COMPLETED);
        r.setProcessedBy(adminUserId);
        refundRepository.save(r);

        // 2) Dao trang thai thanh toan.
        p.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(p);

        // 3) Hoan tra ton kho DIRECT (no-op) + tra ton kho ve PROVIDER (ve provider + KS CHANNEL) roi huy don. (INT-01)
        bookingService.restoreDirectInventory(b);
        bookingService.releaseProviderInventory(b);
        b.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(b);

        // 3b) BP-LOY-02: dao nguoc diem da TICH cho don nay (1 lan, idempotent).
        //     Tranh nong: book -> confirm (cong N diem) -> refund van giu diem (farming + thoi hang).
        reverseLoyaltyPoints(b);

        // 3c) BP-PAY-06: don tra bang NGAN SACH CONG TY -> hoan lai han muc (charge co inverse), tranh ro ri budget.
        if ("COMPANY_BUDGET".equals(p.getMethod()) && b.getCompanyId() != null) {
            companyService.release(b.getCompanyId(), p.getAmount(), b.getId());
        }

        // 3d) BP-VOU-03: trả voucher đã dùng cho đơn này -> khách có thể dùng lại mã cho lần sau.
        voucherService.releaseForBooking(b.getId());

        // 4) Audit qua event - ghi sau khi commit + bat dong bo.
        events.publishEvent(new AuditEvent(adminUserId, "REFUND", "BOOKING", b.getId(),
                "Hoàn " + r.getAmount() + " " + r.getCurrency() + " cho đơn " + b.getPublicCode()
                        + (reason != null && !reason.isBlank() ? " — lý do: " + reason : "")));

        emailService.sendRefunded(b, r.getAmount(), LocaleContextHolder.getLocale());   // email hoan tien (phong thu)
        try {
            userNotificationService.create(b.getUserId(),
                    com.dididi.booking.notification.domain.UserNotificationType.REFUND_COMPLETED,
                    "Hoàn tiền thành công",
                    "Đã hoàn " + r.getAmount() + " " + r.getCurrency() + " cho đơn " + b.getPublicCode() + ".",
                    "/account/bookings", b.getId());
        } catch (Exception ignored) { }
        return r;
    }

    /**
     * BP-LOY-02: ghi 1 giao dich bu (ADJUST, points = -earned) cho don da hoan tien.
     * Idempotent: neu da co ADJUST cho bookingId nay thi bo qua (refund 2 lan khong tru diem 2 lan).
     * earned lay tu tong diem EARN da ghi cho chinh don do.
     */
    private void reverseLoyaltyPoints(Booking b) {
        if (b == null || b.getId() == null) return;
        if (loyaltyRepository.existsByBookingIdAndType(b.getId(), LoyaltyTxnType.ADJUST)) return; // da dao roi
        int earned = loyaltyRepository.sumPointsByBookingAndType(b.getId(), LoyaltyTxnType.EARN);
        if (earned <= 0) return; // khong co diem da tich -> khong can dao
        LoyaltyTransaction t = new LoyaltyTransaction();
        t.setUserId(b.getUserId());
        t.setType(LoyaltyTxnType.ADJUST);
        t.setPoints(-earned);
        t.setBookingId(b.getId());
        t.setDescription("Thu hồi điểm do hoàn tiền đơn " + b.getPublicCode());
        loyaltyRepository.save(t);
    }

    public List<Refund> history() {
        return refundRepository.findAllByOrderByCreatedAtDesc();
    }
}
