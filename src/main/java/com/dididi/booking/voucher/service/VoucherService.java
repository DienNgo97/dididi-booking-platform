package com.dididi.booking.voucher.service;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.voucher.api.dto.VoucherUpsertRequest;
import com.dididi.booking.voucher.domain.Voucher;
import com.dididi.booking.voucher.domain.VoucherDiscountType;
import com.dididi.booking.voucher.repository.VoucherRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

/** Ap/go ma giam gia tren don + CRUD cho admin. */
@Service
public class VoucherService {

    private final VoucherRepository voucherRepository;
    private final BookingRepository bookingRepository;

    public VoucherService(VoucherRepository voucherRepository, BookingRepository bookingRepository) {
        this.voucherRepository = voucherRepository;
        this.bookingRepository = bookingRepository;
    }

    // ---------------- Khach hang: ap / go voucher ----------------

    /** Ap voucher vao don (PENDING_PAYMENT). Cap nhat originalAmount/discountAmount/voucherCode + amount=phai tra. */
    @Transactional
    public Booking apply(String code, Booking b, Long userId) {
        if (b.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BusinessException("CANNOT_APPLY", "Chỉ áp mã khi đơn đang chờ thanh toán", HttpStatus.CONFLICT);
        }
        Voucher v = voucherRepository.findByCodeIgnoreCase(code == null ? "" : code.trim())
                .orElseThrow(() -> new BusinessException("VOUCHER_NOT_FOUND", "Mã giảm giá không tồn tại", HttpStatus.NOT_FOUND));
        if (!v.isActive()) {
            throw new BusinessException("VOUCHER_INACTIVE", "Mã giảm giá đã ngừng áp dụng", HttpStatus.CONFLICT);
        }
        Instant now = Instant.now();
        if (v.getValidFrom() != null && now.isBefore(v.getValidFrom())) {
            throw new BusinessException("VOUCHER_NOT_STARTED", "Mã giảm giá chưa tới ngày áp dụng", HttpStatus.CONFLICT);
        }
        if (v.getValidTo() != null && now.isAfter(v.getValidTo())) {
            throw new BusinessException("VOUCHER_EXPIRED", "Mã giảm giá đã hết hạn", HttpStatus.CONFLICT);
        }

        // Gia goc = originalAmount neu da tung ap voucher, nguoc lai = amount hien tai.
        BigDecimal base = b.getOriginalAmount() != null ? b.getOriginalAmount() : b.getAmount();
        if (base == null) {
            throw new BusinessException("NO_AMOUNT", "Đơn chưa có số tiền", HttpStatus.CONFLICT);
        }
        if (v.getMinOrderAmount() != null && base.compareTo(v.getMinOrderAmount()) < 0) {
            throw new BusinessException("MIN_ORDER",
                    "Đơn tối thiểu " + v.getMinOrderAmount().toBigInteger() + " VND để dùng mã này", HttpStatus.CONFLICT);
        }
        // Gioi han luot dung (tinh tren don da CONFIRMED).
        if (v.getUsageLimit() != null
                && bookingRepository.countByVoucherCodeAndStatus(v.getCode(), BookingStatus.CONFIRMED) >= v.getUsageLimit()) {
            throw new BusinessException("VOUCHER_USED_UP", "Mã giảm giá đã hết lượt sử dụng", HttpStatus.CONFLICT);
        }
        if (v.getPerUserLimit() != null
                && bookingRepository.countByUserIdAndVoucherCodeAndStatus(userId, v.getCode(), BookingStatus.CONFIRMED) >= v.getPerUserLimit()) {
            throw new BusinessException("VOUCHER_USER_LIMIT", "Bạn đã dùng hết lượt cho mã này", HttpStatus.CONFLICT);
        }

        BigDecimal discount = computeDiscount(v, base);
        BigDecimal payable = base.subtract(discount);
        if (payable.signum() < 0) payable = BigDecimal.ZERO;

        b.setOriginalAmount(base);
        b.setDiscountAmount(discount);
        b.setVoucherCode(v.getCode());
        b.setAmount(payable);
        return bookingRepository.save(b);
    }

    /** Go voucher: tra lai amount = originalAmount, xoa discount/voucher. */
    @Transactional
    public Booking remove(Booking b) {
        if (b.getOriginalAmount() != null) {
            b.setAmount(b.getOriginalAmount());
        }
        b.setOriginalAmount(null);
        b.setDiscountAmount(null);
        b.setVoucherCode(null);
        return bookingRepository.save(b);
    }

    private BigDecimal computeDiscount(Voucher v, BigDecimal base) {
        BigDecimal d;
        if (v.getDiscountType() == VoucherDiscountType.PERCENT) {
            d = base.multiply(v.getDiscountValue()).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
            if (v.getMaxDiscount() != null && d.compareTo(v.getMaxDiscount()) > 0) d = v.getMaxDiscount();
        } else {
            d = v.getDiscountValue().setScale(0, RoundingMode.HALF_UP);
        }
        if (d.compareTo(base) > 0) d = base; // khong giam qua gia don
        return d;
    }

    // ---------------- Admin: CRUD ----------------

    public List<Voucher> list() { return voucherRepository.findAllByOrderByIdDesc(); }

    public Voucher get(Long id) {
        return voucherRepository.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy voucher", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public Voucher create(VoucherUpsertRequest req) {
        if (req.code() == null || req.code().isBlank()) {
            throw new BusinessException("INVALID", "Thiếu mã voucher", HttpStatus.BAD_REQUEST);
        }
        if (voucherRepository.existsByCodeIgnoreCase(req.code().trim())) {
            throw new BusinessException("CODE_EXISTS", "Mã voucher đã tồn tại", HttpStatus.CONFLICT);
        }
        Voucher v = new Voucher();
        apply(v, req);
        v.setCode(req.code().trim());
        return voucherRepository.save(v);
    }

    @Transactional
    public Voucher update(Long id, VoucherUpsertRequest req) {
        Voucher v = get(id);
        apply(v, req);
        return voucherRepository.save(v);
    }

    @Transactional
    public void delete(Long id) {
        voucherRepository.delete(get(id));
    }

    private void apply(Voucher v, VoucherUpsertRequest req) {
        if (req.description() != null) v.setDescription(req.description());
        if (req.discountType() != null) v.setDiscountType(VoucherDiscountType.valueOf(req.discountType()));
        if (req.discountValue() != null) v.setDiscountValue(req.discountValue());
        v.setMaxDiscount(req.maxDiscount());
        v.setMinOrderAmount(req.minOrderAmount());
        v.setUsageLimit(req.usageLimit());
        v.setPerUserLimit(req.perUserLimit());
        v.setValidFrom(req.validFrom());
        v.setValidTo(req.validTo());
        if (req.active() != null) v.setActive(req.active());
    }
}
