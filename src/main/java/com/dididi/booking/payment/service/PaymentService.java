package com.dididi.booking.payment.service;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.payment.domain.entity.Payment;
import com.dididi.booking.payment.domain.enums.PaymentStatus;
import com.dididi.booking.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Cong thanh toan GIA LAP (stub). Khong tich hop cong that.
 */
@Service
public class PaymentService {

    private static final SecureRandom RND = new SecureRandom();
    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public Optional<Payment> findByBooking(Long bookingId) {
        return paymentRepository.findByBookingId(bookingId);
    }

    /** Gia lap thanh toan thanh cong. */
    public Payment pay(Booking booking) {
        Payment p = paymentRepository.findByBookingId(booking.getId()).orElseGet(Payment::new);
        p.setBookingId(booking.getId());
        p.setAmount(booking.getAmount());
        p.setCurrency(booking.getCurrency());
        p.setMethod("MOCK");
        p.setStatus(PaymentStatus.PAID);
        p.setTransactionRef("TX-" + Math.abs(RND.nextLong() % 1_000_000_0000L));
        return paymentRepository.save(p);
    }

    /** Thanh toan bang ngan sach cong ty (B2B): ghi nhan PAID, method COMPANY_BUDGET. */
    public Payment payByCompany(Booking booking) {
        Payment p = paymentRepository.findByBookingId(booking.getId()).orElseGet(Payment::new);
        p.setBookingId(booking.getId());
        p.setAmount(booking.getAmount());
        p.setCurrency(booking.getCurrency());
        p.setMethod("COMPANY_BUDGET");
        p.setStatus(PaymentStatus.PAID);
        p.setTransactionRef("CO-" + booking.getPublicCode());
        return paymentRepository.save(p);
    }

    // ===================== VNPay (Phase 8a) =====================

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * Tao/ghi de ban ghi Payment o trang thai PENDING cho VNPay va sinh ma giao dich (vnp_TxnRef).
     * txnRef = publicCode + "_" + timestamp (duy nhat moi lan bam thanh toan).
     */
    public Payment initiateVnpay(Booking booking) {
        Payment p = paymentRepository.findByBookingId(booking.getId()).orElseGet(Payment::new);
        String txnRef = booking.getPublicCode() + "_"
                + LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).format(TS);
        p.setBookingId(booking.getId());
        p.setAmount(booking.getAmount());
        p.setCurrency(booking.getCurrency());
        p.setMethod("VNPAY");
        p.setStatus(PaymentStatus.PENDING);
        p.setTransactionRef(txnRef);
        p.setGatewayTxnNo(null);
        p.setBankCode(null);
        p.setResponseCode(null);
        p.setPayDate(null);
        return paymentRepository.save(p);
    }

    public Optional<Payment> findByTxnRef(String txnRef) {
        return paymentRepository.findByTransactionRef(txnRef);
    }

    public Payment markPaid(Payment p, String gatewayTxnNo, String bankCode,
                            String responseCode, String payDate) {
        p.setStatus(PaymentStatus.PAID);
        p.setGatewayTxnNo(gatewayTxnNo);
        p.setBankCode(bankCode);
        p.setResponseCode(responseCode);
        p.setPayDate(payDate);
        return paymentRepository.save(p);
    }

    public Payment markFailed(Payment p, String responseCode) {
        p.setStatus(PaymentStatus.FAILED);
        p.setResponseCode(responseCode);
        return paymentRepository.save(p);
    }
}
