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
import com.dididi.booking.ops.domain.OpsAlert;
import com.dididi.booking.ops.service.OpsAlertService;
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
    private final OpsAlertService opsAlerts;
    private final com.dididi.booking.settlement.service.PartnerSettlementService settlementService;
    private final com.dididi.booking.hotel.repository.HotelRepository hotelRepository;
    private final com.dididi.booking.flight.repository.FlightRepository flightRepository;
    private final BigDecimal superAdminThreshold;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RefundService.class);

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
                         OpsAlertService opsAlerts,
                         com.dididi.booking.settlement.service.PartnerSettlementService settlementService,
                         com.dididi.booking.hotel.repository.HotelRepository hotelRepository,
                         com.dididi.booking.flight.repository.FlightRepository flightRepository,
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
        this.opsAlerts = opsAlerts;
        this.settlementService = settlementService;
        this.hotelRepository = hotelRepository;
        this.flightRepository = flightRepository;
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
        // P1-4: tien CO THAT SU ve tay khach chua? Chi hai truong hop la "xong ngay":
        //   - COMPANY_BUDGET: khong co tien mat, chi tra lai han muc (da lam o buoc 3c);
        //   - MOCK: moi truong dev, khong co dong nao roi khoi tai khoan ai.
        // Con VNPAY: he thong KHONG goi API hoan cua cong -> tien van nam o Dididi, ke toan phai
        // chuyen khoan tay. Danh dau COMPLETED va gui mail "da hoan tien" luc nay la NOI DOI voi khach.
        boolean tienVeNgay = "COMPANY_BUDGET".equals(p.getMethod()) || "MOCK".equals(p.getMethod());

        Refund r = new Refund();
        r.setBookingId(b.getId());
        r.setPaymentId(p.getId());
        r.setAmount(p.getAmount());
        r.setCurrency(p.getCurrency());
        r.setReason(reason);
        r.setStatus(tienVeNgay ? RefundStatus.COMPLETED : RefundStatus.PENDING_TRANSFER);
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

        // 3e) P1-8: nếu đơn thuộc kỳ đối soát ĐÃ CHỐT/ĐÃ TRẢ, ghi bút toán trừ vào kỳ sau —
        // nếu không, đối tác giữ luôn phần đã nhận cho một đơn không còn tồn tại.
        ghiDieuChinhDoiSoat(b);

        // 4) Audit qua event - ghi sau khi commit + bat dong bo.
        events.publishEvent(new AuditEvent(adminUserId, "REFUND", "BOOKING", b.getId(),
                "Hoàn " + r.getAmount() + " " + r.getCurrency() + " cho đơn " + b.getPublicCode()
                        + (reason != null && !reason.isBlank() ? " — lý do: " + reason : "")));

        if (tienVeNgay) {
            baoKhachDaHoanTien(b, r);
        } else {
            // Chua chuyen tien -> noi that voi khach, va tao VIEC cho ke toan (canh bao van hanh).
            try {
                userNotificationService.create(b.getUserId(),
                        com.dididi.booking.notification.domain.UserNotificationType.REFUND_COMPLETED,
                        "Đã tiếp nhận yêu cầu hoàn tiền",
                        "Đơn " + b.getPublicCode() + " đã được huỷ. Khoản " + r.getAmount() + " "
                                + r.getCurrency() + " sẽ được chuyển về tài khoản thanh toán của bạn"
                                + " trong 3-5 ngày làm việc.",
                        "/account/bookings", b.getId());
            } catch (Exception ignored) { }
            opsAlerts.raise(OpsAlert.Type.REFUND_PENDING_TRANSFER, OpsAlert.Severity.CRITICAL,
                    b.getId(), b.getPublicCode(),
                    "Đã huỷ đơn và ghi sổ hoàn " + r.getAmount() + " " + r.getCurrency()
                            + " nhưng TIỀN CHƯA CHUYỂN — thanh toán qua " + p.getMethod()
                            + ", hệ thống không tự hoàn qua cổng được.",
                    "Kế toán chuyển khoản cho khách rồi vào Đơn & hoàn tiền bấm 'Đã chuyển tiền' kèm mã giao dịch.");
        }
        return r;
    }

    /**
     * P1-4: đánh dấu ĐÃ CHUYỂN TIỀN cho một khoản hoàn đang chờ — đây mới là lúc khách được báo
     * "đã hoàn tiền". Bắt buộc có mã giao dịch để còn đối chiếu sao kê.
     */
    @Transactional
    public Refund markTransferred(Long refundId, Long adminUserId, String transactionRef) {
        if (transactionRef == null || transactionRef.isBlank()) {
            throw new BusinessException("TRANSFER_REF_REQUIRED",
                    "Nhập mã giao dịch chuyển khoản để đối chiếu sao kê", HttpStatus.BAD_REQUEST);
        }
        Refund r = refundRepository.findById(refundId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy khoản hoàn", HttpStatus.NOT_FOUND));
        if (r.getStatus() != RefundStatus.PENDING_TRANSFER) {
            throw new BusinessException("REFUND_NOT_PENDING",
                    "Khoản hoàn này không ở trạng thái chờ chuyển tiền (hiện tại: " + r.getStatus() + ")",
                    HttpStatus.CONFLICT);
        }
        r.setStatus(RefundStatus.COMPLETED);
        r.setGatewayRefundNo(transactionRef.trim());
        r.setProcessedBy(adminUserId);
        refundRepository.save(r);

        Booking b = bookingRepository.findById(r.getBookingId()).orElse(null);
        if (b != null) {
            baoKhachDaHoanTien(b, r);
            events.publishEvent(new AuditEvent(adminUserId, "REFUND_TRANSFERRED", "BOOKING", b.getId(),
                    "Đã chuyển " + r.getAmount() + " " + r.getCurrency() + " cho đơn " + b.getPublicCode()
                            + " — mã giao dịch " + r.getGatewayRefundNo()));
        }
        opsAlerts.autoResolve(OpsAlert.Type.REFUND_PENDING_TRANSFER, r.getBookingId(),
                "Kế toán đã chuyển tiền, mã " + r.getGatewayRefundNo());
        return r;
    }

    /** Khoản chờ chuyển tiền — danh sách việc của kế toán. */
    @Transactional(readOnly = true)
    public List<Refund> pendingTransfers() {
        return refundRepository.findByStatusOrderByIdDesc(RefundStatus.PENDING_TRANSFER);
    }

    /**
     * P1-8: quy đơn vừa hoàn về đúng đối tác + kỳ dịch vụ, rồi nhờ đối soát ghi bút toán bù nếu
     * kỳ đó đã chốt. Khách sạn CHANNEL (không vendor) -> HOTEL_PMS; vé -> mã hãng. Các loại khác
     * (KS tự doanh, KS có vendor) không phát sinh công nợ đối tác nên bỏ qua.
     */
    private void ghiDieuChinhDoiSoat(Booking b) {
        try {
            if (b.getType() == com.dididi.booking.booking.domain.enums.BookingType.HOTEL) {
                if (b.getCheckOut() == null || b.getTargetId() == null) return;
                var h = hotelRepository.findById(b.getTargetId()).orElse(null);
                if (h == null || h.getVendorId() != null
                        || h.getSource() == com.dididi.booking.hotel.domain.enums.HotelSource.DIRECT) {
                    return;
                }
                settlementService.ghiDieuChinhNeuKyDaChot(b,
                        com.dididi.booking.settlement.service.PartnerSettlementService.HOTEL_PMS,
                        java.time.YearMonth.from(b.getCheckOut()));
            } else if (b.getType() == com.dididi.booking.booking.domain.enums.BookingType.FLIGHT) {
                if (b.getTravelDate() == null || b.getTargetId() == null) return;
                var f = flightRepository.findById(b.getTargetId()).orElse(null);
                if (f == null || f.getAirlineCode() == null) return;
                settlementService.ghiDieuChinhNeuKyDaChot(b, f.getAirlineCode(),
                        java.time.YearMonth.from(b.getTravelDate()));
            }
        } catch (Exception ex) {
            // Không để lỗi ghi bù làm hỏng việc hoàn tiền cho khách; nhưng phải kêu to.
            log.error("[ops] Không ghi được điều chỉnh đối soát cho đơn {}: {}", b.getPublicCode(), ex.toString());
        }
    }

    private void baoKhachDaHoanTien(Booking b, Refund r) {
        emailService.sendRefunded(b, r.getAmount(), LocaleContextHolder.getLocale());
        try {
            userNotificationService.create(b.getUserId(),
                    com.dididi.booking.notification.domain.UserNotificationType.REFUND_COMPLETED,
                    "Hoàn tiền thành công",
                    "Đã hoàn " + r.getAmount() + " " + r.getCurrency() + " cho đơn " + b.getPublicCode() + ".",
                    "/account/bookings", b.getId());
        } catch (Exception ignored) { }
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
