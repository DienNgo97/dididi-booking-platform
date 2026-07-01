package com.dididi.booking.payment;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.booking.service.BookingService;
import com.dididi.booking.corporate.service.CompanyService;
import com.dididi.booking.corporate.service.CorporateBookingService;
import com.dididi.booking.group.service.GroupBookingService;
import com.dididi.booking.payment.domain.entity.Payment;
import com.dididi.booking.payment.domain.enums.PaymentStatus;
import com.dididi.booking.payment.repository.PaymentAttemptRepository;
import com.dididi.booking.payment.service.PaymentService;
import com.dididi.booking.payment.vnpay.VnPayService;
import com.dididi.booking.payment.web.PaymentWebController;
import com.dididi.booking.voucher.service.VoucherService;
import com.dididi.booking.web.CurrentUser;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BP-PAY-01: VNPay return phai kiem tra so tien. Neu vnp_Amount KHONG khop payment.amount*100,
 * dù chu ky hop le va response code "00", thi don KHONG duoc xac nhan (markPaid/markConfirmed khong chay).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VnPayReturnAmountCheckTest {

    @Mock BookingService bookingService;
    @Mock BookingRepository bookingRepository;
    @Mock PaymentService paymentService;
    @Mock VnPayService vnPayService;
    @Mock CurrentUser currentUser;
    @Mock CompanyService companyService;
    @Mock CorporateBookingService corporateBookingService;
    @Mock VoucherService voucherService;
    @Mock GroupBookingService groupService;
    @Mock PaymentAttemptRepository paymentAttemptRepository;

    PaymentWebController controller;

    @BeforeEach
    void setUp() {
        controller = new PaymentWebController(bookingService, bookingRepository, paymentService, vnPayService,
                currentUser, companyService, corporateBookingService, voucherService, groupService, paymentAttemptRepository);
    }

    private Map<String, String> baseParams(String txnRef, long vnpAmount) {
        Map<String, String> p = new HashMap<>();
        p.put("vnp_TxnRef", txnRef);
        p.put("vnp_ResponseCode", "00");
        p.put("vnp_TransactionStatus", "00");
        p.put("vnp_Amount", String.valueOf(vnpAmount));
        p.put("vnp_TransactionNo", "999");
        p.put("vnp_BankCode", "NCB");
        p.put("vnp_PayDate", "20260101000000");
        return p;
    }

    @Test
    void mismatchedAmount_doesNotConfirm() {
        String txnRef = "DD-ABC123_20260101000000";
        Payment p = new Payment();
        p.setBookingId(1L);
        p.setAmount(new BigDecimal("1000000"));   // dung = 100000000 (x100)
        p.setStatus(PaymentStatus.PENDING);

        Booking b = new Booking();
        b.setStatus(BookingStatus.PENDING_PAYMENT);

        when(vnPayService.isValid(any())).thenReturn(true);
        when(paymentService.findByTxnRef(txnRef)).thenReturn(Optional.of(p));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(b));

        // vnp_Amount THIEU (chi 100*100 = 10000 thay vi 100000000) -> gian lan tra thieu.
        Map<String, String> params = baseParams(txnRef, 10_000L);
        String view = controller.vnpayReturn(params, mock(RedirectAttributes.class), mock(HttpSession.class));

        // KHONG markPaid, KHONG markConfirmed.
        verify(paymentService, never()).markPaid(any(), anyString(), anyString(), anyString(), anyString());
        verify(bookingService, never()).markConfirmed(any());
        assertThat(view).startsWith("redirect:");
    }

    @Test
    void matchingAmount_confirms() {
        String txnRef = "DD-ABC123_20260101000000";
        Payment p = new Payment();
        p.setBookingId(1L);
        p.setAmount(new BigDecimal("1000000"));
        p.setStatus(PaymentStatus.PENDING);

        Booking b = new Booking();
        b.setStatus(BookingStatus.PENDING_PAYMENT);

        when(vnPayService.isValid(any())).thenReturn(true);
        when(paymentService.findByTxnRef(txnRef)).thenReturn(Optional.of(p));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(b));

        // vnp_Amount dung = 1000000 * 100.
        Map<String, String> params = baseParams(txnRef, 100_000_000L);
        controller.vnpayReturn(params, mock(RedirectAttributes.class), mock(HttpSession.class));

        verify(paymentService, times(1)).markPaid(any(), anyString(), anyString(), anyString(), anyString());
        verify(bookingService, times(1)).markConfirmed(b);
    }
}
