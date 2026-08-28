package com.dididi.booking.voucher.service;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.voucher.api.dto.VoucherUpsertRequest;
import com.dididi.booking.voucher.domain.Voucher;
import com.dididi.booking.voucher.domain.VoucherDiscountType;
import com.dididi.booking.voucher.domain.VoucherRedemption;
import com.dididi.booking.voucher.repository.VoucherRedemptionRepository;
import com.dididi.booking.voucher.repository.VoucherRepository;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final VoucherRedemptionRepository redemptionRepository;

    public VoucherService(VoucherRepository voucherRepository, BookingRepository bookingRepository,
                          VoucherRedemptionRepository redemptionRepository) {
        this.voucherRepository = voucherRepository;
        this.bookingRepository = bookingRepository;
        this.redemptionRepository = redemptionRepository;
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
        // BP-LOY-01: voucher rieng (doi tu diem) chi nguoi so huu duoc dung.
        if (v.getOwnerUserId() != null && !v.getOwnerUserId().equals(userId)) {
            throw new BusinessException("VOUCHER_NOT_OWNED",
                    "Mã giảm giá này thuộc về người dùng khác", HttpStatus.FORBIDDEN);
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
        // BP-VOU-03: 1 lượt/user enforce qua VoucherRedemption (unique code+user) VÀ chặn dùng lại khi suất
        // đang gắn 1 đơn CÒN HIỆU LỰC. (Trước đây phần áp giảm giá chạy vô điều kiện -> mã usageLimit=1 vẫn
        // áp được cho nhiều đơn = lạm dụng.) Nếu đơn cũ đã huỷ/hết hạn -> trả suất về cho đơn mới (HOÀN voucher).
        java.util.Optional<VoucherRedemption> existing =
                redemptionRepository.findByVoucherCodeAndUserId(v.getCode(), userId);
        if (existing.isPresent()) {
            VoucherRedemption red = existing.get();
            Long heldBooking = red.getBookingId();
            if (heldBooking != null && !heldBooking.equals(b.getId())) {
                Booking other = bookingRepository.findById(heldBooking).orElse(null);
                boolean otherActive = other != null
                        && (other.getStatus() == BookingStatus.PENDING_PAYMENT
                            || other.getStatus() == BookingStatus.CONFIRMED);
                if (otherActive) {
                    throw new BusinessException("VOUCHER_IN_USE",
                            "Bạn đã dùng mã này cho đơn " + other.getPublicCode()
                                    + ". Hãy gỡ mã ở đơn đó hoặc huỷ đơn trước khi dùng lại.", HttpStatus.CONFLICT);
                }
                // Đơn cũ không còn hiệu lực -> chuyển suất sang đơn hiện tại (dùng lại mã cho lần sau).
                red.setBookingId(b.getId());
                redemptionRepository.save(red);
            }
            // heldBooking == đơn hiện tại -> áp lại trên cùng đơn (idempotent), không làm gì thêm.
        } else {
            if (v.getUsageLimit() != null) {
                // P1-13: khoá dòng voucher TRƯỚC khi đếm. Đếm-rồi-ghi mà không khoá thì hai khách
                // cuối cùng đều thấy "còn suất" và cùng ghi -> phát vượt số lượng flash-sale.
                voucherRepository.findByIdForUpdate(v.getId());
                if (redemptionRepository.countByVoucherCode(v.getCode()) >= v.getUsageLimit()) {
                    throw new BusinessException("VOUCHER_USED_UP", "Mã giảm giá đã hết lượt sử dụng", HttpStatus.CONFLICT);
                }
            }
            try {
                redemptionRepository.saveAndFlush(new VoucherRedemption(v.getCode(), userId, b.getId()));
            } catch (DataIntegrityViolationException ex) {
                throw new BusinessException("VOUCHER_USER_LIMIT", "Bạn đã dùng hết lượt cho mã này", HttpStatus.CONFLICT);
            }
        }

        BigDecimal discount = computeDiscount(v, base);
        BigDecimal tierDiscount = b.getTierDiscountAmount() != null ? b.getTierDiscountAmount() : BigDecimal.ZERO;
        BigDecimal payable = base.subtract(discount).subtract(tierDiscount);
        if (payable.signum() < 0) payable = BigDecimal.ZERO;

        b.setOriginalAmount(base);
        b.setDiscountAmount(discount);
        b.setVoucherCode(v.getCode());
        b.setAmount(payable);
        return bookingRepository.save(b);
    }

    /** Go voucher: amount tra ve gia goc tru uu dai hang; xoa discount/voucher (giu uu dai hang). */
    @Transactional
    public Booking remove(Booking b) {
        // BP-VOU-02: tha suat su dung da chiem khi ap (de user co the doi sang ma khac / dung lai sau).
        if (b.getId() != null) {
            redemptionRepository.findByBookingId(b.getId()).ifPresent(redemptionRepository::delete);
        }
        BigDecimal tierDiscount = b.getTierDiscountAmount() != null ? b.getTierDiscountAmount() : BigDecimal.ZERO;
        if (b.getOriginalAmount() != null) {
            BigDecimal afterTier = b.getOriginalAmount().subtract(tierDiscount);
            if (afterTier.signum() < 0) afterTier = BigDecimal.ZERO;
            b.setAmount(afterTier);
        }
        b.setDiscountAmount(null);
        b.setVoucherCode(null);
        // Khong con uu dai hang -> bo gia goc (don ve trang thai khong giam gia).
        if (tierDiscount.signum() == 0) {
            b.setOriginalAmount(null);
        }
        return bookingRepository.save(b);
    }

    /**
     * BP-VOU-03: TRẢ voucher khi đơn bị huỷ / hoàn tiền / hết hạn -> xoá suất redemption gắn đơn này,
     * để khách dùng lại mã cho lần sau. Gọi từ RefundService + admin huỷ đơn.
     * (Đơn hết hạn tự động: apply() đã tự "trỏ lại" suất khi đơn cũ không còn hiệu lực, nên vẫn dùng lại được.)
     */
    @Transactional
    public void releaseForBooking(Long bookingId) {
        if (bookingId == null) return;
        redemptionRepository.findByBookingId(bookingId).ifPresent(redemptionRepository::delete);
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
        // BP-VOU-01: PUT/PATCH mot phan chi ghi de field CO MAT trong request (non-null).
        // Truoc day caps/limits/validity bi set vo dieu kien -> payload thieu field = xoa trang cap
        // (PERCENT mat tran giam -> giam toan don; mat usageLimit -> dung vo han). Gio null-guard tat ca.
        if (req.description() != null) v.setDescription(req.description());
        if (req.discountType() != null) v.setDiscountType(VoucherDiscountType.valueOf(req.discountType()));
        if (req.discountValue() != null) v.setDiscountValue(req.discountValue());
        if (req.maxDiscount() != null) v.setMaxDiscount(req.maxDiscount());
        if (req.minOrderAmount() != null) v.setMinOrderAmount(req.minOrderAmount());
        if (req.usageLimit() != null) v.setUsageLimit(req.usageLimit());
        if (req.perUserLimit() != null) v.setPerUserLimit(req.perUserLimit());
        if (req.validFrom() != null) v.setValidFrom(req.validFrom());
        if (req.validTo() != null) v.setValidTo(req.validTo());
        if (req.active() != null) v.setActive(req.active());
    }
}
