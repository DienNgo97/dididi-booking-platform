package com.dididi.booking.payment;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.booking.service.BookingService;
import com.dididi.booking.loyalty.domain.LoyaltyTransaction;
import com.dididi.booking.loyalty.domain.LoyaltyTxnType;
import com.dididi.booking.loyalty.repository.LoyaltyTransactionRepository;
import com.dididi.booking.notification.EmailService;
import com.dididi.booking.payment.domain.entity.Payment;
import com.dididi.booking.payment.domain.entity.Refund;
import com.dididi.booking.payment.domain.enums.PaymentStatus;
import com.dididi.booking.payment.repository.PaymentRepository;
import com.dididi.booking.payment.repository.RefundRepository;
import com.dididi.booking.payment.service.RefundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BP-LOY-02: refund ghi 1 giao dich bu (ADJUST = -earned) — DUNG 1 LAN (idempotent).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoyaltyRefundReversalTest {

    @Mock BookingRepository bookingRepository;
    @Mock BookingService bookingService;
    @Mock PaymentRepository paymentRepository;
    @Mock RefundRepository refundRepository;
    @Mock LoyaltyTransactionRepository loyaltyRepository;
    @Mock EmailService emailService;
    @Mock ApplicationEventPublisher events;

    @Captor ArgumentCaptor<LoyaltyTransaction> txnCaptor;

    RefundService service;

    @BeforeEach
    void setUp() {
        service = new RefundService(bookingRepository, bookingService, paymentRepository, refundRepository,
                loyaltyRepository, emailService, events, new BigDecimal("5000000"));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Booking confirmedBooking() {
        Booking b = new Booking();
        b.setId(50L);
        b.setPublicCode("DD-REF001");
        b.setUserId(7L);
        b.setStatus(BookingStatus.CONFIRMED);
        b.setAmount(new BigDecimal("1000000"));
        return b;
    }

    private Payment paidPayment() {
        Payment p = new Payment();
        p.setId(80L);
        p.setBookingId(50L);
        p.setAmount(new BigDecimal("1000000"));
        p.setCurrency("VND");
        p.setStatus(PaymentStatus.PAID);
        return p;
    }

    @Test
    void refund_writesCompensatingAdjustOnce() {
        Booking b = confirmedBooking();
        when(bookingRepository.findByPublicCode("DD-REF001")).thenReturn(Optional.of(b));
        when(paymentRepository.findByBookingId(50L)).thenReturn(Optional.of(paidPayment()));
        // Chua co ADJUST cho don nay; da tich 1000 diem EARN.
        when(loyaltyRepository.existsByBookingIdAndType(50L, LoyaltyTxnType.ADJUST)).thenReturn(false);
        when(loyaltyRepository.sumPointsByBookingAndType(50L, LoyaltyTxnType.EARN)).thenReturn(1000);

        service.refund("DD-REF001", 1L, "khach yeu cau", true);

        verify(loyaltyRepository, times(1)).save(txnCaptor.capture());
        LoyaltyTransaction t = txnCaptor.getValue();
        assertThat(t.getType()).isEqualTo(LoyaltyTxnType.ADJUST);
        assertThat(t.getPoints()).isEqualTo(-1000);     // dao nguoc dung so diem da tich
        assertThat(t.getBookingId()).isEqualTo(50L);
        assertThat(t.getUserId()).isEqualTo(7L);
        // Provider inventory cung phai duoc tra (INT-01).
        verify(bookingService, times(1)).releaseProviderInventory(b);
    }

    @Test
    void refund_secondTime_doesNotReverseAgain() {
        Booking b = confirmedBooking();
        when(bookingRepository.findByPublicCode("DD-REF001")).thenReturn(Optional.of(b));
        when(paymentRepository.findByBookingId(50L)).thenReturn(Optional.of(paidPayment()));
        // Da co ADJUST -> idempotent, khong dao nua.
        when(loyaltyRepository.existsByBookingIdAndType(50L, LoyaltyTxnType.ADJUST)).thenReturn(true);

        service.refund("DD-REF001", 1L, "lan 2", true);

        verify(loyaltyRepository, never()).save(any(LoyaltyTransaction.class));
    }
}
