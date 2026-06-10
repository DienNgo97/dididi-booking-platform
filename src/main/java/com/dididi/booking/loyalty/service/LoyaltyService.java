package com.dididi.booking.loyalty.service;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.common.exception.BusinessException;
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
import java.util.List;
import java.util.UUID;

/** Diem thuong: tich khi don xac nhan, xem so du/hang/lich su, doi diem lay voucher. */
@Service
public class LoyaltyService {

    private final LoyaltyTransactionRepository repository;
    private final VoucherRepository voucherRepository;

    /** So VND cho 1 diem khi TICH (vd 1000 -> 1tr chi tieu = 1000 diem). */
    @Value("${app.loyalty.vnd-per-point:1000}")
    private long vndPerPoint;

    /** Gia tri 1 diem (VND) khi DOI sang voucher (vd 100 -> 1000 diem = 100.000d). */
    @Value("${app.loyalty.redeem-point-value:100}")
    private long redeemPointValue;

    /** So diem toi thieu moi lan doi. */
    @Value("${app.loyalty.min-redeem:1000}")
    private int minRedeem;

    public LoyaltyService(LoyaltyTransactionRepository repository, VoucherRepository voucherRepository) {
        this.repository = repository;
        this.voucherRepository = voucherRepository;
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

    public int balance(Long userId) { return repository.balance(userId); }

    public int lifetimeEarned(Long userId) { return repository.sumByType(userId, LoyaltyTxnType.EARN); }

    public List<LoyaltyTransaction> history(Long userId) { return repository.findByUserIdOrderByIdDesc(userId); }

    /** Hang thanh vien theo tong diem da tich. */
    public String tier(Long userId) {
        int earned = lifetimeEarned(userId);
        if (earned >= 2000) return "GOLD";
        if (earned >= 500) return "SILVER";
        return "BRONZE";
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
        t.setDescription("Đổi " + points + " điểm lấy voucher " + code);
        repository.save(t);
        return v;
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
