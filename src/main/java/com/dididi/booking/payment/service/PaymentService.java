package com.dididi.booking.payment.service;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.payment.domain.entity.Payment;
import com.dididi.booking.payment.domain.enums.PaymentStatus;
import com.dididi.booking.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
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
}
