package com.dididi.booking.voucher;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.voucher.api.dto.VoucherUpsertRequest;
import com.dididi.booking.voucher.domain.Voucher;
import com.dididi.booking.voucher.domain.VoucherDiscountType;
import com.dididi.booking.voucher.repository.VoucherRedemptionRepository;
import com.dididi.booking.voucher.repository.VoucherRepository;
import com.dididi.booking.voucher.service.VoucherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * BP-VOU-01 (partial PUT giu caps), BP-LOY-01 (voucher gan owner tu choi user khac),
 * va discount khong am.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VoucherServiceTest {

    @Mock VoucherRepository voucherRepository;
    @Mock BookingRepository bookingRepository;
    @Mock VoucherRedemptionRepository redemptionRepository;

    VoucherService service;

    @BeforeEach
    void setUp() {
        service = new VoucherService(voucherRepository, bookingRepository, redemptionRepository);
        when(voucherRepository.save(any(Voucher.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- BP-VOU-01: PUT mot phan KHONG xoa caps/limits ----------
    @Test
    void partialUpdate_keepsCapsAndLimits() {
        Voucher existing = new Voucher();
        existing.setCode("SALE10");
        existing.setDiscountType(VoucherDiscountType.PERCENT);
        existing.setDiscountValue(new BigDecimal("10"));
        existing.setMaxDiscount(new BigDecimal("500000"));
        existing.setMinOrderAmount(new BigDecimal("200000"));
        existing.setUsageLimit(100);
        existing.setPerUserLimit(1);
        when(voucherRepository.findById(7L)).thenReturn(Optional.of(existing));

        // PUT chi doi 'active' (moi field khac null) -> KHONG duoc xoa cap/limit.
        VoucherUpsertRequest req = new VoucherUpsertRequest(
                null, null, null, null,
                null, null, null, null,
                null, null, Boolean.FALSE);
        Voucher updated = service.update(7L, req);

        assertThat(updated.getMaxDiscount()).isEqualByComparingTo("500000");
        assertThat(updated.getMinOrderAmount()).isEqualByComparingTo("200000");
        assertThat(updated.getUsageLimit()).isEqualTo(100);
        assertThat(updated.getPerUserLimit()).isEqualTo(1);
        assertThat(updated.isActive()).isFalse();
    }

    // ---------- BP-LOY-01: voucher gan owner -> user khac bi tu choi ----------
    @Test
    void ownerBoundVoucher_rejectsOtherUser() {
        Voucher v = new Voucher();
        v.setCode("PT-ABCD1234");
        v.setDiscountType(VoucherDiscountType.FIXED);
        v.setDiscountValue(new BigDecimal("100000"));
        v.setActive(true);
        v.setOwnerUserId(1L);   // thuoc user 1
        when(voucherRepository.findByCodeIgnoreCase("PT-ABCD1234")).thenReturn(Optional.of(v));

        Booking b = new Booking();
        b.setStatus(BookingStatus.PENDING_PAYMENT);
        b.setAmount(new BigDecimal("1000000"));

        // user 2 ap dung -> VOUCHER_NOT_OWNED.
        assertThatThrownBy(() -> service.apply("PT-ABCD1234", b, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("người dùng khác");
    }

    @Test
    void ownerBoundVoucher_allowsOwner() {
        Voucher v = new Voucher();
        v.setCode("PT-ABCD1234");
        v.setDiscountType(VoucherDiscountType.FIXED);
        v.setDiscountValue(new BigDecimal("100000"));
        v.setActive(true);
        v.setOwnerUserId(1L);
        when(voucherRepository.findByCodeIgnoreCase("PT-ABCD1234")).thenReturn(Optional.of(v));
        when(redemptionRepository.existsByVoucherCodeAndUserId(anyString(), anyLong())).thenReturn(false);
        when(redemptionRepository.countByVoucherCode(anyString())).thenReturn(0L);

        Booking b = new Booking();
        b.setStatus(BookingStatus.PENDING_PAYMENT);
        b.setAmount(new BigDecimal("1000000"));

        Booking out = service.apply("PT-ABCD1234", b, 1L);   // chu so huu -> OK
        assertThat(out.getVoucherCode()).isEqualTo("PT-ABCD1234");
        assertThat(out.getAmount()).isEqualByComparingTo("900000");   // 1.000.000 - 100.000
    }

    // ---------- discount khong vuot don (amount khong am) ----------
    @Test
    void discount_neverNegative() {
        Voucher v = new Voucher();
        v.setCode("BIG");
        v.setDiscountType(VoucherDiscountType.FIXED);
        v.setDiscountValue(new BigDecimal("5000000"));   // lon hon don
        v.setActive(true);
        when(voucherRepository.findByCodeIgnoreCase("BIG")).thenReturn(Optional.of(v));
        when(redemptionRepository.existsByVoucherCodeAndUserId(anyString(), anyLong())).thenReturn(false);
        when(redemptionRepository.countByVoucherCode(anyString())).thenReturn(0L);

        Booking b = new Booking();
        b.setStatus(BookingStatus.PENDING_PAYMENT);
        b.setAmount(new BigDecimal("1000000"));

        Booking out = service.apply("BIG", b, 9L);
        assertThat(out.getAmount().signum()).isGreaterThanOrEqualTo(0);  // khong am
        assertThat(out.getAmount()).isEqualByComparingTo("0");           // giam toi da = gia don
    }
}
