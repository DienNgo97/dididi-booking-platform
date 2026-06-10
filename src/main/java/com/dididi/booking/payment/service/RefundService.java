package com.dididi.booking.payment.service;

import com.dididi.booking.audit.event.AuditEvent;
import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.booking.service.BookingService;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.notification.EmailService;
import com.dididi.booking.payment.domain.entity.Payment;
import com.dididi.booking.payment.domain.entity.Refund;
import com.dididi.booking.payment.domain.enums.PaymentStatus;
import com.dididi.booking.payment.domain.enums.RefundStatus;
import com.dididi.booking.payment.repository.PaymentRepository;
import com.dididi.booking.payment.repository.RefundRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
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
    private final EmailService emailService;
    private final ApplicationEventPublisher events;
    private final BigDecimal superAdminThreshold;

    public RefundService(BookingRepository bookingRepository, BookingService bookingService,
                         PaymentRepository paymentRepository, RefundRepository refundRepository,
                         EmailService emailService, ApplicationEventPublisher events,
                         @Value("${app.refund.super-admin-threshold:10000000}") BigDecimal superAdminThreshold) {
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.emailService = emailService;
        this.events = events;
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

        // 3) Hoan tra ton kho DIRECT (neu co) roi huy don.
        bookingService.restoreDirectInventory(b);
        b.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(b);

        // 4) Audit qua event - ghi sau khi commit + bat dong bo.
        events.publishEvent(new AuditEvent(adminUserId, "REFUND", "BOOKING", b.getId(),
                "Hoàn " + r.getAmount() + " " + r.getCurrency() + " cho đơn " + b.getPublicCode()
                        + (reason != null && !reason.isBlank() ? " — lý do: " + reason : "")));

        emailService.sendRefunded(b, r.getAmount());   // email hoan tien (phong thu)
        return r;
    }

    public List<Refund> history() {
        return refundRepository.findAllByOrderByCreatedAtDesc();
    }
}
