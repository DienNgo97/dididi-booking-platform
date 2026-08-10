package com.dididi.booking.promo.service;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.Role;
import com.dididi.booking.identity.domain.enums.UserStatus;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.loyalty.service.LoyaltyService;
import com.dididi.booking.notification.domain.UserNotificationType;
import com.dididi.booking.notification.service.UserNotificationService;
import com.dididi.booking.promo.domain.PromoCampaign;
import com.dididi.booking.promo.domain.PromoCampaignType;
import com.dididi.booking.promo.domain.PromoGrant;
import com.dididi.booking.promo.repository.PromoCampaignRepository;
import com.dididi.booking.promo.repository.PromoGrantRepository;
import com.dididi.booking.voucher.domain.Voucher;
import com.dididi.booking.voucher.domain.VoucherDiscountType;
import com.dididi.booking.voucher.repository.VoucherRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * KHUYẾN MÃI CÁ NHÂN HOÁ — sinh voucher RIÊNG cho từng khách theo 4 chương trình:
 * sinh nhật, khách quay lại (win-back), tri ân hạng thành viên, chào mừng khách mới.
 *
 * Nguyên tắc thiết kế:
 *  - Voucher tặng = bản ghi {@link Voucher} có {@code ownerUserId} -> tái dùng TOÀN BỘ luồng áp mã
 *    đã có (VoucherService kiểm tra chủ sở hữu, hạn dùng, đơn tối thiểu...). Không viết luồng giảm giá mới.
 *  - Mỗi lần tặng ghi 1 {@link PromoGrant} với "cycleKey" -> unique DB chặn tặng trùng,
 *    nên job chạy lại bao nhiêu lần cũng an toàn.
 *  - Mỗi khách được xử lý trong 1 giao dịch RIÊNG (REQUIRES_NEW): 1 người lỗi không làm hỏng cả đợt.
 */
@Service
public class PersonalPromoService {

    private static final Logger log = LoggerFactory.getLogger(PersonalPromoService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PromoCampaignRepository campaignRepository;
    private final PromoGrantRepository grantRepository;
    private final VoucherRepository voucherRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final LoyaltyService loyaltyService;
    private final UserNotificationService userNotificationService;

    public PersonalPromoService(PromoCampaignRepository campaignRepository, PromoGrantRepository grantRepository,
                                VoucherRepository voucherRepository, UserRepository userRepository,
                                BookingRepository bookingRepository, LoyaltyService loyaltyService,
                                UserNotificationService userNotificationService) {
        this.campaignRepository = campaignRepository;
        this.grantRepository = grantRepository;
        this.voucherRepository = voucherRepository;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.loyaltyService = loyaltyService;
        this.userNotificationService = userNotificationService;
    }

    // ==================== CHẠY ĐỊNH KỲ (gọi từ PersonalPromoScheduler) ====================

    /** Quét toàn bộ chương trình đang bật. @return số voucher đã tặng. */
    public int runAll() {
        int n = 0;
        n += runBirthday();
        n += runWinBack();
        n += runTierReward();
        n += runWelcome();
        return n;
    }

    /**
     * CHÀO MỪNG KHÁCH MỚI: khách ACTIVE đăng ký trong {@code validDays} ngày gần đây và chưa nhận quà.
     * Quét ở scheduler (thay vì hook vào từng luồng đăng ký web/Google/mobile) nên bao phủ mọi cách
     * tạo tài khoản mà không phải sửa module identity.
     */
    public int runWelcome() {
        PromoCampaign c = activeCampaign(PromoCampaignType.WELCOME);
        if (c == null) return 0;
        Instant since = Instant.now().minus(Math.max(1, c.getValidDays()), ChronoUnit.DAYS);
        int granted = 0;
        for (User u : userRepository.findByRoleAndStatus(Role.CUSTOMER, UserStatus.ACTIVE)) {
            if (u.getCreatedAt() == null || u.getCreatedAt().isBefore(since)) continue;   // chỉ khách MỚI
            if (grantSafely(c, u.getId(), "ONCE", "Khách mới đăng ký")) granted++;
        }
        if (granted > 0) log.info("[promo] Chào mừng khách mới: đã tặng {} voucher.", granted);
        return granted;
    }

    /** SINH NHẬT: khách có ngày sinh trùng hôm nay -> tặng 1 lần/năm. */
    public int runBirthday() {
        PromoCampaign c = activeCampaign(PromoCampaignType.BIRTHDAY);
        if (c == null) return 0;
        LocalDate today = LocalDate.now(ZONE);
        List<User> users = userRepository.findBirthdayCustomers(
                Role.CUSTOMER, UserStatus.ACTIVE, today.getMonthValue(), today.getDayOfMonth());
        String cycle = String.valueOf(today.getYear());
        int granted = 0;
        for (User u : users) {
            if (grantSafely(c, u.getId(), cycle, "Sinh nhật " + fmtDay(u.getBirthDate()))) granted++;
        }
        if (granted > 0) log.info("[promo] Sinh nhật: đã tặng {} voucher ({} khách sinh nhật hôm nay).", granted, users.size());
        return granted;
    }

    /** KHÁCH QUAY LẠI: đã từng đặt nhưng đơn gần nhất cách đây > thresholdDays -> tối đa 1 lần/tháng. */
    public int runWinBack() {
        PromoCampaign c = activeCampaign(PromoCampaignType.WIN_BACK);
        if (c == null) return 0;
        Instant cutoff = Instant.now().minus(c.getThresholdDays(), ChronoUnit.DAYS);
        // Đơn CONFIRMED gần nhất của từng khách (1 query gộp, không lặp từng người).
        Map<Long, Instant> lastByUser = new HashMap<>();
        for (Booking b : bookingRepository.findByStatusOrderByCreatedAtDesc(BookingStatus.CONFIRMED)) {
            if (b.getUserId() == null || b.getCreatedAt() == null) continue;
            lastByUser.merge(b.getUserId(), b.getCreatedAt(), (a, x) -> a.isAfter(x) ? a : x);
        }
        LocalDate today = LocalDate.now(ZONE);
        String cycle = String.format("%d-%02d", today.getYear(), today.getMonthValue());
        int granted = 0;
        for (Map.Entry<Long, Instant> e : lastByUser.entrySet()) {
            if (e.getValue().isAfter(cutoff)) continue;             // vẫn đang đặt đều -> bỏ qua
            long days = ChronoUnit.DAYS.between(e.getValue(), Instant.now());
            if (grantSafely(c, e.getKey(), cycle, days + " ngày chưa đặt đơn nào")) granted++;
        }
        if (granted > 0) log.info("[promo] Khách quay lại: đã tặng {} voucher.", granted);
        return granted;
    }

    /** TRI ÂN HẠNG: khách hạng >= minTier, mỗi chu kỳ (thresholdDays) 1 lần. */
    public int runTierReward() {
        PromoCampaign c = activeCampaign(PromoCampaignType.TIER_REWARD);
        if (c == null) return 0;
        LocalDate today = LocalDate.now(ZONE);
        String cycle = today.getYear() + "-Q" + ((today.getMonthValue() - 1) / 3 + 1);
        int granted = 0;
        for (User u : userRepository.findByRoleAndStatus(Role.CUSTOMER, UserStatus.ACTIVE)) {
            String tier = safeTier(u.getId());
            if (!tierAtLeast(tier, c.getMinTier())) continue;
            if (grantSafely(c, u.getId(), cycle, "Hạng " + tier)) granted++;
        }
        if (granted > 0) log.info("[promo] Tri ân hạng thành viên: đã tặng {} voucher.", granted);
        return granted;
    }

    // ==================== KÍCH HOẠT THEO SỰ KIỆN ====================

    /** Gọi khi khách vừa đăng ký xong: tặng voucher chào mừng (1 lần/đời). */
    public void onUserRegistered(Long userId) {
        PromoCampaign c = activeCampaign(PromoCampaignType.WELCOME);
        if (c == null || userId == null) return;
        grantSafely(c, userId, "ONCE", "Khách mới đăng ký");
    }

    // ==================== TẶNG 1 VOUCHER ====================

    /**
     * Tặng voucher cho 1 khách trong GIAO DỊCH RIÊNG (1 người lỗi không làm hỏng cả đợt).
     * @return true nếu vừa tặng mới; false nếu đã tặng ở chu kỳ này hoặc gặp lỗi.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean grantSafely(PromoCampaign c, Long userId, String cycleKey, String note) {
        try {
            if (grantRepository.existsByTypeAndUserIdAndCycleKey(c.getType(), userId, cycleKey)) return false;

            Voucher v = new Voucher();
            v.setCode(uniqueCode(c.getType().getCodePrefix()));
            v.setDescription(c.getTitle());
            v.setDiscountType(c.getDiscountType());
            v.setDiscountValue(c.getDiscountValue());
            v.setMaxDiscount(c.getMaxDiscount());
            v.setMinOrderAmount(c.getMinOrderAmount());
            v.setUsageLimit(1);
            v.setPerUserLimit(1);
            v.setOwnerUserId(userId);                      // voucher RIÊNG của khách này
            v.setValidFrom(Instant.now());
            v.setValidTo(Instant.now().plus(Math.max(1, c.getValidDays()), ChronoUnit.DAYS));
            v.setActive(true);
            voucherRepository.save(v);

            PromoGrant g = new PromoGrant();
            g.setType(c.getType());
            g.setUserId(userId);
            g.setCycleKey(cycleKey);
            g.setVoucherCode(v.getCode());
            g.setNote(note);
            grantRepository.saveAndFlush(g);               // unique chặn tặng trùng (2 job chạy song song)

            notify(c, userId, v);
            return true;
        } catch (DataIntegrityViolationException dup) {
            return false;                                  // đã có người/luồng khác tặng ở chu kỳ này
        } catch (Exception ex) {
            log.warn("[promo] Không tặng được {} cho user {}: {}", c.getType(), userId, ex.toString());
            return false;
        }
    }

    private void notify(PromoCampaign c, Long userId, Voucher v) {
        try {
            String body = describeDiscount(v) + " · Mã " + v.getCode()
                    + " · Dùng trước " + LocalDate.ofInstant(v.getValidTo(), ZONE);
            userNotificationService.create(userId, UserNotificationType.PROMO_GRANTED,
                    c.getTitle(), body, "/account/offers", null);
        } catch (Exception ignored) { }
    }

    private String describeDiscount(Voucher v) {
        if (v.getDiscountType() == VoucherDiscountType.PERCENT) {
            String s = "Giảm " + v.getDiscountValue().stripTrailingZeros().toPlainString() + "%";
            if (v.getMaxDiscount() != null) {
                s += " (tối đa " + v.getMaxDiscount().longValue() + "đ)";
            }
            return s;
        }
        return "Giảm " + v.getDiscountValue().longValue() + "đ";
    }

    // ==================== TRUY VẤN CHO KHÁCH ====================

    /** Voucher cá nhân của khách (do hệ thống tặng hoặc đổi điểm), mới nhất trước. */
    @Transactional(readOnly = true)
    public List<Voucher> myVouchers(Long userId) {
        if (userId == null) return List.of();
        return voucherRepository.findByOwnerUserIdOrderByIdDesc(userId);
    }

    /** Có ưu đãi nào ĐANG dùng được không (để hiện banner trang chủ). */
    @Transactional(readOnly = true)
    public Voucher firstUsable(Long userId) {
        Instant now = Instant.now();
        for (Voucher v : myVouchers(userId)) {
            if (!v.isActive()) continue;
            if (v.getValidTo() != null && v.getValidTo().isBefore(now)) continue;
            if (v.getValidFrom() != null && v.getValidFrom().isAfter(now)) continue;
            return v;
        }
        return null;
    }

    @Transactional(readOnly = true)
    public List<PromoGrant> myGrants(Long userId) {
        return userId == null ? List.of() : grantRepository.findByUserIdOrderByIdDesc(userId);
    }

    // ==================== HỖ TRỢ ====================

    private PromoCampaign activeCampaign(PromoCampaignType type) {
        return campaignRepository.findByType(type).filter(PromoCampaign::isEnabled).orElse(null);
    }

    private String uniqueCode(String prefix) {
        for (int i = 0; i < 20; i++) {
            String code = prefix + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            if (!voucherRepository.existsByCodeIgnoreCase(code)) return code;
        }
        return prefix + "-" + System.nanoTime();
    }

    private String safeTier(Long userId) {
        try {
            return loyaltyService.tier(userId);
        } catch (Exception e) {
            return "SILVER";
        }
    }

    private static final List<String> TIER_ORDER = List.of("SILVER", "GOLD", "PLATINUM", "DIAMOND");

    private static boolean tierAtLeast(String tier, String min) {
        int a = TIER_ORDER.indexOf(tier == null ? "SILVER" : tier.toUpperCase());
        int b = TIER_ORDER.indexOf(min == null ? "GOLD" : min.toUpperCase());
        return a >= 0 && b >= 0 && a >= b;
    }

    private static String fmtDay(LocalDate d) {
        return d == null ? "" : String.format("%02d/%02d", d.getDayOfMonth(), d.getMonthValue());
    }

    /** Danh sách chương trình cho admin (tạo sẵn nếu thiếu). */
    @Transactional
    public List<PromoCampaign> campaigns() {
        List<PromoCampaign> all = new ArrayList<>(campaignRepository.findAllByOrderByIdAsc());
        return all;
    }
}
