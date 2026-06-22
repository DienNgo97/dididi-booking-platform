package com.dididi.booking.loyalty.service;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.loyalty.api.dto.RedeemedVoucherDto;
import com.dididi.booking.loyalty.domain.LoyaltyTransaction;
import com.dididi.booking.loyalty.domain.LoyaltyTxnType;
import com.dididi.booking.loyalty.repository.LoyaltyTransactionRepository;
import com.dididi.booking.voucher.domain.Voucher;
import com.dididi.booking.voucher.domain.VoucherDiscountType;
import com.dididi.booking.voucher.repository.VoucherRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Diem thuong: tich khi don xac nhan, xem so du/hang/lich su, doi diem lay voucher. */
@Service
public class LoyaltyService {

    private final LoyaltyTransactionRepository repository;
    private final VoucherRepository voucherRepository;
    private final BookingRepository bookingRepository;

    /** Diem het han sau 1 nam ke tu khi tich (don tich diem phat sinh). */
    private static final long POINT_VALID_DAYS = 365;
    /** Nguong xep hang theo tong diem tich con han. */
    public static final int TIER_GOLD = 5000;
    public static final int TIER_PLATINUM = 15000;
    public static final int TIER_DIAMOND = 30000;

    /** So VND cho 1 diem khi TICH (vd 1000 -> 1tr chi tieu = 1000 diem). */
    @Value("${app.loyalty.vnd-per-point:1000}")
    private long vndPerPoint;

    /** Gia tri 1 diem (VND) khi DOI sang voucher (vd 100 -> 1000 diem = 100.000d). */
    @Value("${app.loyalty.redeem-point-value:100}")
    private long redeemPointValue;

    /** So diem toi thieu moi lan doi. */
    @Value("${app.loyalty.min-redeem:1000}")
    private int minRedeem;

    public LoyaltyService(LoyaltyTransactionRepository repository, VoucherRepository voucherRepository,
                          BookingRepository bookingRepository) {
        this.repository = repository;
        this.voucherRepository = voucherRepository;
        this.bookingRepository = bookingRepository;
    }

    /** Tich diem cho 1 don da xac nhan (idempotent theo bookingId). Goi tu BookingService.markConfirmed. */
    @Transactional
    public void earnForBooking(Booking b) {
        if (b == null || b.getUserId() == null || b.getAmount() == null) return;
        if (b.getId() != null && repository.existsByBookingIdAndType(b.getId(), LoyaltyTxnType.EARN)) return;
        int points = b.getAmount().divide(BigDecimal.valueOf(vndPerPoint), 0, RoundingMode.FLOOR).intValue();
        if (points <= 0) return;
        LoyaltyTransaction t = new LoyaltyTransaction();
        t.setUserId(b.getUserId());
        t.setType(LoyaltyTxnType.EARN);
        t.setPoints(points);
        t.setBookingId(b.getId());
        t.setDescription("Tích điểm đơn " + b.getPublicCode());
        repository.save(t);
    }

    public int balance(Long userId) {
        return repository.balanceValid(userId, LoyaltyTxnType.EARN, pointCutoff());
    }

    /** Tong diem tich CON HAN (de xep hang). */
    public int validEarned(Long userId) {
        return repository.earnedValid(userId, LoyaltyTxnType.EARN, pointCutoff());
    }

    private Instant pointCutoff() { return Instant.now().minus(POINT_VALID_DAYS, ChronoUnit.DAYS); }

    public int lifetimeEarned(Long userId) { return repository.sumByType(userId, LoyaltyTxnType.EARN); }

    public List<LoyaltyTransaction> history(Long userId) { return repository.findByUserIdOrderByIdDesc(userId); }

    /** Hang thanh vien theo tong diem tich con han (12 thang gan nhat). */
    public String tier(Long userId) {
        int p = validEarned(userId);
        if (p >= TIER_DIAMOND) return "DIAMOND";
        if (p >= TIER_PLATINUM) return "PLATINUM";
        if (p >= TIER_GOLD) return "GOLD";
        return "SILVER";
    }

    /**
     * Giam gia theo hang tren tong hoa don (base), co tran. SILVER khong giam.
     *  GOLD 2% (toi da 500.000d), PLATINUM 4% (toi da 1.000.000d), DIAMOND 10% (toi da 2.000.000d).
     */
    public BigDecimal tierDiscount(Long userId, BigDecimal base) {
        if (base == null || base.signum() <= 0) return BigDecimal.ZERO;
        int percent;
        long cap;
        switch (tier(userId)) {
            case "GOLD":     percent = 2;  cap = 500_000L;   break;
            case "PLATINUM": percent = 4;  cap = 1_000_000L; break;
            case "DIAMOND":  percent = 10; cap = 2_000_000L; break;
            default:         return BigDecimal.ZERO;   // SILVER
        }
        BigDecimal d = base.multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR);
        BigDecimal capBd = BigDecimal.valueOf(cap);
        return d.compareTo(capBd) > 0 ? capBd : d;
    }

    public long redeemPointValue() { return redeemPointValue; }
    public int minRedeem() { return minRedeem; }

    /** Doi diem lay voucher giam gia (FIXED, 1 luot). Tra ve voucher da tao. */
    @Transactional
    public Voucher redeemForVoucher(Long userId, int points) {
        if (points < minRedeem) {
            throw new BusinessException("MIN_REDEEM", "Tối thiểu " + minRedeem + " điểm mỗi lần đổi", HttpStatus.BAD_REQUEST);
        }
        int bal = balance(userId);
        if (points > bal) {
            throw new BusinessException("NOT_ENOUGH_POINTS", "Không đủ điểm (số dư: " + bal + ")", HttpStatus.CONFLICT);
        }
        BigDecimal value = BigDecimal.valueOf((long) points * redeemPointValue);

        String code;
        do { code = "PT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(); }
        while (voucherRepository.existsByCodeIgnoreCase(code));

        Voucher v = new Voucher();
        v.setCode(code);
        v.setDescription("Voucher đổi từ " + points + " điểm");
        v.setDiscountType(VoucherDiscountType.FIXED);
        v.setDiscountValue(value);
        v.setUsageLimit(1);
        v.setPerUserLimit(1);
        v.setValidTo(Instant.now().plus(90, ChronoUnit.DAYS));
        v.setActive(true);
        voucherRepository.save(v);

        LoyaltyTransaction t = new LoyaltyTransaction();
        t.setUserId(userId);
        t.setType(LoyaltyTxnType.REDEEM);
        t.setPoints(-points);
        t.setVoucherCode(code);
        t.setDescription("Đổi " + points + " điểm lấy voucher " + code);
        repository.save(t);
        return v;
    }

    /** Danh sach voucher khach DA DOI tu diem + trang thai da/chua dung (cho tab "Voucher da doi"). */
    public List<RedeemedVoucherDto> redeemedVouchers(Long userId) {
        List<LoyaltyTransaction> txns =
                repository.findByUserIdAndTypeAndVoucherCodeIsNotNullOrderByIdDesc(userId, LoyaltyTxnType.REDEEM);
        List<RedeemedVoucherDto> out = new ArrayList<>();
        for (LoyaltyTransaction t : txns) {
            long value = (long) (-t.getPoints()) * redeemPointValue;
            Instant expiresAt = t.getCreatedAt() != null ? t.getCreatedAt().plus(90, ChronoUnit.DAYS) : null;
            boolean used = bookingRepository.countByUserIdAndVoucherCodeAndStatus(
                    userId, t.getVoucherCode(), BookingStatus.CONFIRMED) > 0;
            out.add(new RedeemedVoucherDto(t.getVoucherCode(), value, t.getCreatedAt(), expiresAt, used));
        }
        return out;
    }

    /** Admin chinh tay diem (cong/tru). */
    @Transactional
    public void adjust(Long userId, int points, String note) {
        LoyaltyTransaction t = new LoyaltyTransaction();
        t.setUserId(userId);
        t.setType(LoyaltyTxnType.ADJUST);
        t.setPoints(points);
        t.setDescription(note != null ? note : "Điều chỉnh điểm");
        repository.save(t);
    }
}
