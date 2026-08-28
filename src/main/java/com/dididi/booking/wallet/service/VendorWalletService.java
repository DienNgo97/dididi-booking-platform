package com.dididi.booking.wallet.service;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.commission.service.CommissionService;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.common.i18n.I18nSupport;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.UserStatus;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.notification.domain.UserNotificationType;
import com.dididi.booking.notification.service.UserNotificationService;
import com.dididi.booking.wallet.domain.entity.PayoutRequest;
import com.dididi.booking.wallet.domain.entity.VendorLedgerEntry;
import com.dididi.booking.wallet.domain.enums.LedgerType;
import com.dididi.booking.wallet.domain.enums.PayoutStatus;
import com.dididi.booking.wallet.repository.PayoutRequestRepository;
import com.dididi.booking.wallet.repository.VendorLedgerEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

/**
 * Ví doanh thu vendor — mô hình SỔ CÁI (thiết kế chốt với Jay 19/08/2026).
 *
 * Bất biến quan trọng: KHẢ DỤNG = (bút toán đã tới hạn availableFrom = checkOut + 3 ngày)
 * − (tiền giữ do đơn có yêu cầu huỷ treo) − (tiền giữ chỗ của payout chưa chốt).
 * Vì chính sách "quá checkOut + 3 ngày không còn hoàn tiền" được chặn ngay tại RefundService,
 * tiền đã khả dụng là tiền KHÔNG THỂ bị đòi lại → ví không bao giờ âm.
 */
@Service
public class VendorWalletService {

    private static final Logger log = LoggerFactory.getLogger(VendorWalletService.class);

    /** Cửa sổ khiếu nại sau trả phòng (ngày) — dùng CHUNG với guard hoàn tiền ở RefundService. */
    public static final int COMPLAINT_WINDOW_DAYS = 3;

    private static final EnumSet<PayoutStatus> HOLDING = EnumSet.of(PayoutStatus.REQUESTED, PayoutStatus.PROCESSING);

    private final VendorLedgerEntryRepository ledgerRepository;
    private final PayoutRequestRepository payoutRepository;
    private final UserRepository userRepository;
    private final CommissionService commissionService;
    private final UserNotificationService userNotificationService;

    @Value("${app.payout.min-amount:500000}")
    private BigDecimal minPayout;

    public VendorWalletService(VendorLedgerEntryRepository ledgerRepository,
                               PayoutRequestRepository payoutRepository,
                               UserRepository userRepository,
                               CommissionService commissionService,
                               UserNotificationService userNotificationService) {
        this.ledgerRepository = ledgerRepository;
        this.payoutRepository = payoutRepository;
        this.userRepository = userRepository;
        this.commissionService = commissionService;
        this.userNotificationService = userNotificationService;
    }

    // ================= SỐ DƯ =================

    public record WalletSummary(BigDecimal total, BigDecimal available, BigDecimal pending,
                                BigDecimal held, BigDecimal payoutHolding, BigDecimal minPayout) {}

    @Transactional(readOnly = true)
    public WalletSummary summary(Long vendorId) {
        LocalDate today = LocalDate.now();
        BigDecimal total = ledgerRepository.totalBalance(vendorId);
        BigDecimal matured = ledgerRepository.maturedBalance(vendorId, today);
        BigDecimal held = ledgerRepository.heldByPendingCancel(vendorId, today);
        BigDecimal holding = payoutRepository.holdingAmount(vendorId, HOLDING);
        BigDecimal available = matured.subtract(held).subtract(holding);
        if (available.signum() < 0) available = BigDecimal.ZERO;   // phòng hờ, theo thiết kế không xảy ra
        BigDecimal pending = total.subtract(matured);
        return new WalletSummary(total, available, pending, held, holding, minPayout);
    }

    @Transactional(readOnly = true)
    public Page<VendorLedgerEntry> ledger(Long vendorId, int page, int size) {
        return ledgerRepository.findByVendorIdOrderByIdDesc(vendorId, PageRequest.of(page, Math.min(size, 50)));
    }

    @Transactional(readOnly = true)
    public Page<PayoutRequest> payouts(Long vendorId, int page, int size) {
        return payoutRepository.findByVendorIdOrderByIdDesc(vendorId, PageRequest.of(page, Math.min(size, 50)));
    }

    // ================= RÚT TIỀN =================

    /**
     * Tạo yêu cầu rút. CHỐNG RACE (bài học FIX-M9): khoá pessimistic trên dòng User của vendor
     * -> hai request song song của CÙNG vendor xếp hàng, tổng tiền giữ chỗ không bao giờ vượt khả dụng.
     */
    @Transactional
    public PayoutRequest requestPayout(Long vendorId, BigDecimal amount) {
        User vendor = userRepository.findByIdForUpdate(vendorId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy tài khoản", HttpStatus.NOT_FOUND));
        if (vendor.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("VENDOR_NOT_ACTIVE",
                    I18nSupport.msg("err.VENDOR_NOT_ACTIVE", "Tài khoản đang bị khoá/chờ duyệt — không thể rút tiền."),
                    HttpStatus.FORBIDDEN);
        }
        if (isBlank(vendor.getBankName()) || isBlank(vendor.getBankAccountNo()) || isBlank(vendor.getBankAccountHolder())) {
            throw new BusinessException("BANK_ACCOUNT_MISSING",
                    I18nSupport.msg("err.BANK_ACCOUNT_MISSING", "Vui lòng nhập tài khoản ngân hàng nhận tiền trước khi rút."),
                    HttpStatus.CONFLICT);
        }
        if (amount == null || amount.signum() <= 0 || amount.remainder(BigDecimal.ONE).signum() != 0) {
            throw new BusinessException("INVALID_AMOUNT",
                    I18nSupport.msg("err.INVALID_AMOUNT", "Số tiền rút không hợp lệ."), HttpStatus.BAD_REQUEST);
        }
        if (amount.compareTo(minPayout) < 0) {
            throw new BusinessException("BELOW_MIN_PAYOUT",
                    I18nSupport.msg("err.BELOW_MIN_PAYOUT", "Số tiền rút tối thiểu là {0}đ.",
                            String.format("%,d", minPayout.longValue()).replace(',', '.')),
                    HttpStatus.BAD_REQUEST);
        }
        WalletSummary s = summary(vendorId);   // tính trong transaction đang giữ khoá vendor
        if (amount.compareTo(s.available()) > 0) {
            throw new BusinessException("INSUFFICIENT_BALANCE",
                    I18nSupport.msg("err.INSUFFICIENT_BALANCE", "Số tiền vượt quá số dư khả dụng."),
                    HttpStatus.CONFLICT);
        }

        PayoutRequest p = new PayoutRequest();
        p.setVendorId(vendorId);
        p.setAmount(amount.setScale(0, RoundingMode.HALF_UP));
        p.setStatus(PayoutStatus.REQUESTED);
        // SNAPSHOT tài khoản nhận tiền — vendor đổi STK sau đó không ảnh hưởng yêu cầu này
        p.setBankName(vendor.getBankName().trim());
        p.setBankAccountNo(vendor.getBankAccountNo().trim());
        p.setBankAccountHolder(vendor.getBankAccountHolder().trim());
        return payoutRepository.save(p);
    }

    /** Vendor tự huỷ khi còn REQUESTED. Đổi trạng thái NGUYÊN TỬ để không đụng processor đang nhận việc. */
    @Transactional
    public PayoutRequest cancelPayout(Long vendorId, Long payoutId) {
        PayoutRequest p = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy yêu cầu rút", HttpStatus.NOT_FOUND));
        if (!p.getVendorId().equals(vendorId)) {   // IDOR: không huỷ hộ ví người khác
            throw new BusinessException("FORBIDDEN", "Không có quyền với yêu cầu này", HttpStatus.FORBIDDEN);
        }
        int changed = payoutRepository.transition(payoutId, PayoutStatus.REQUESTED, PayoutStatus.CANCELLED);
        if (changed == 0) {
            throw new BusinessException("CANNOT_CANCEL_PAYOUT",
                    I18nSupport.msg("err.CANNOT_CANCEL_PAYOUT", "Yêu cầu đã được xử lý — không thể huỷ nữa."),
                    HttpStatus.CONFLICT);
        }
        p.setStatus(PayoutStatus.CANCELLED);
        p.setProcessedAt(java.time.Instant.now());
        return payoutRepository.save(p);
    }

    // ================= ADMIN CHI TIỀN THỦ CÔNG (P0-1, 28/08) =================
    // Ở PRODUCTION mock ngân hàng bị ép TẮT nên KHÔNG có đường nào đưa yêu cầu ra khỏi REQUESTED
    // -> tiền vendor bị giữ chỗ treo vĩnh viễn. Hai hàm dưới là đường xử lý tay của admin:
    // admin chuyển khoản ngoài hệ thống rồi ghi nhận kết quả vào đây.

    /** Admin xác nhận ĐÃ chuyển khoản cho vendor (kèm mã UNC) -> sinh bút toán PAYOUT. */
    @Transactional
    public PayoutRequest adminMarkPaid(Long payoutId, String transactionRef) {
        if (isBlank(transactionRef)) {
            throw new BusinessException("PAYMENT_REF_REQUIRED",
                    I18nSupport.msg("err.PAYMENT_REF_REQUIRED", "Vui lòng nhập mã UNC/chuyển khoản."),
                    HttpStatus.BAD_REQUEST);
        }
        PayoutRequest p = openPayout(payoutId);
        completePayout(p, transactionRef.trim());   // idempotent theo payoutId
        return p;
    }

    /** Admin từ chối/ghi nhận chuyển khoản thất bại -> tiền nhả về khả dụng cho vendor rút lại. */
    @Transactional
    public PayoutRequest adminMarkFailed(Long payoutId, String reason) {
        PayoutRequest p = openPayout(payoutId);
        failPayout(p, (reason == null || reason.isBlank())
                ? I18nSupport.msg("err.PAYOUT_REJECTED_DEFAULT", "Admin từ chối yêu cầu rút tiền.")
                : reason.trim());
        return p;
    }

    /** Chỉ yêu cầu CHƯA chốt mới xử lý được; đã PAID/FAILED/CANCELLED thì dừng (chống bấm hai lần). */
    private PayoutRequest openPayout(Long payoutId) {
        PayoutRequest p = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy yêu cầu rút", HttpStatus.NOT_FOUND));
        if (p.getStatus() != PayoutStatus.REQUESTED && p.getStatus() != PayoutStatus.PROCESSING) {
            throw new BusinessException("PAYOUT_ALREADY_SETTLED",
                    I18nSupport.msg("err.PAYOUT_ALREADY_SETTLED",
                            "Yêu cầu này đã được xử lý xong (trạng thái {0}).", p.getStatus().name()),
                    HttpStatus.CONFLICT);
        }
        return p;
    }

    // ================= TÀI KHOẢN NGÂN HÀNG =================

    @Transactional
    public User updateBankAccount(Long vendorId, String bankName, String accountNo, String holder) {
        if (isBlank(bankName) || isBlank(accountNo) || isBlank(holder)) {
            throw new BusinessException("BANK_FIELDS_REQUIRED",
                    I18nSupport.msg("err.BANK_FIELDS_REQUIRED", "Vui lòng nhập đủ tên ngân hàng, số tài khoản và chủ tài khoản."),
                    HttpStatus.BAD_REQUEST);
        }
        String no = accountNo.trim();
        if (!no.matches("[0-9]{4,30}")) {
            throw new BusinessException("BANK_ACCOUNT_INVALID",
                    I18nSupport.msg("err.BANK_ACCOUNT_INVALID", "Số tài khoản chỉ gồm 4-30 chữ số."),
                    HttpStatus.BAD_REQUEST);
        }
        User vendor = userRepository.findById(vendorId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy tài khoản", HttpStatus.NOT_FOUND));
        vendor.setBankName(bankName.trim());
        vendor.setBankAccountNo(no);
        vendor.setBankAccountHolder(holder.trim());
        return userRepository.save(vendor);
    }

    // ================= GHI SỔ (scheduler gọi) =================

    /**
     * Ghi EARNING cho các đơn HOTEL CONFIRMED (có vendor) còn thiếu bút toán.
     * Idempotent: unique (booking_id, type) trong DB; lần chạy đầu chính là BACKFILL toàn bộ lịch sử.
     */
    @Transactional
    public int recordMissingEarnings(int batchSize) {
        List<Object[]> rows = ledgerRepository.findConfirmedHotelBookingsMissingEarning(PageRequest.of(0, batchSize));
        int written = 0;
        for (Object[] row : rows) {
            Booking b = (Booking) row[0];
            Long vendorId = (Long) row[1];
            if (b.getAmount() == null || b.getAmount().signum() <= 0) {
                log.warn("[wallet] Bỏ qua đơn {} (amount null/âm) — không ghi bút toán.", b.getPublicCode());
                continue;
            }
            BigDecimal rate = commissionService.effectiveRate(vendorId);
            BigDecimal commission = b.getAmount().multiply(rate).setScale(0, RoundingMode.HALF_UP);
            VendorLedgerEntry e = new VendorLedgerEntry();
            e.setVendorId(vendorId);
            e.setType(LedgerType.EARNING);
            e.setBookingId(b.getId());
            e.setGross(b.getAmount());
            e.setCommissionRate(rate);
            e.setCommissionAmount(commission);
            e.setNetAmount(b.getAmount().subtract(commission));
            e.setAvailableFrom(availableFrom(b));
            e.setNote(b.getPublicCode());
            try {
                ledgerRepository.saveAndFlush(e);
                written++;
            } catch (org.springframework.dao.DataIntegrityViolationException dup) {
                // unique (booking_id, type) — scheduler khác vừa ghi xong, bỏ qua an toàn
            }
        }
        return written;
    }

    /** Đảo bút toán cho đơn đã CANCELLED (hoàn tiền luôn toàn phần) — copy availableFrom của bút toán gốc. */
    @Transactional
    public int recordMissingReversals() {
        int written = 0;
        for (VendorLedgerEntry earning : ledgerRepository.findEarningsNeedingReversal()) {
            VendorLedgerEntry r = new VendorLedgerEntry();
            r.setVendorId(earning.getVendorId());
            r.setType(LedgerType.REVERSAL);
            r.setBookingId(earning.getBookingId());
            r.setGross(earning.getGross() == null ? null : earning.getGross().negate());
            r.setCommissionRate(earning.getCommissionRate());   // rate CHỐT của bút toán gốc
            r.setCommissionAmount(earning.getCommissionAmount() == null ? null : earning.getCommissionAmount().negate());
            r.setNetAmount(earning.getNetAmount().negate());
            r.setAvailableFrom(earning.getAvailableFrom());     // triệt tiêu đúng bucket với bút toán gốc
            r.setNote(earning.getNote());
            try {
                ledgerRepository.saveAndFlush(r);
                written++;
            } catch (org.springframework.dao.DataIntegrityViolationException dup) {
                // đã có REVERSAL — bỏ qua
            }
        }
        return written;
    }

    /** Chốt PAID: sinh bút toán PAYOUT âm (idempotent theo payoutId) + thông báo chuông. */
    @Transactional
    public void completePayout(PayoutRequest p, String transactionRef) {
        if (ledgerRepository.existsByPayoutIdAndType(p.getId(), LedgerType.PAYOUT)) {
            return;                                             // đã chốt trước đó (restart giữa chừng)
        }
        VendorLedgerEntry e = new VendorLedgerEntry();
        e.setVendorId(p.getVendorId());
        e.setType(LedgerType.PAYOUT);
        e.setPayoutId(p.getId());
        e.setNetAmount(p.getAmount().negate());
        e.setAvailableFrom(LocalDate.now());
        e.setNote(transactionRef);
        ledgerRepository.save(e);

        p.setStatus(PayoutStatus.PAID);
        p.setTransactionRef(transactionRef);
        p.setProcessedAt(java.time.Instant.now());
        payoutRepository.save(p);
        userNotificationService.create(p.getVendorId(), UserNotificationType.PAYOUT_PAID,
                I18nSupport.msg("notif.payoutPaid.title", "Rút tiền thành công"),
                I18nSupport.msg("notif.payoutPaid.body", "Đã chuyển {0}đ tới tài khoản {1}. Mã GD: {2}",
                        String.format("%,d", p.getAmount().longValue()).replace(',', '.'),
                        maskAccount(p.getBankAccountNo()), transactionRef),
                "/vendor/wallet", p.getId());
    }

    @Transactional
    public void failPayout(PayoutRequest p, String reason) {
        p.setStatus(PayoutStatus.FAILED);
        p.setFailReason(reason);
        p.setProcessedAt(java.time.Instant.now());
        payoutRepository.save(p);                               // tiền giữ chỗ tự nhả (không còn trong HOLDING)
        userNotificationService.create(p.getVendorId(), UserNotificationType.PAYOUT_FAILED,
                I18nSupport.msg("notif.payoutFailed.title", "Rút tiền thất bại"),
                I18nSupport.msg("notif.payoutFailed.body", "Yêu cầu rút {0}đ không thành công: {1}. Tiền đã trả về số dư khả dụng.",
                        String.format("%,d", p.getAmount().longValue()).replace(',', '.'), reason),
                "/vendor/wallet", p.getId());
    }

    // ================= helpers =================

    /** availableFrom = checkOut + cửa sổ khiếu nại; đơn thiếu checkOut (dữ liệu cũ) lùi 7 ngày từ lúc tạo cho an toàn. */
    private LocalDate availableFrom(Booking b) {
        if (b.getCheckOut() != null) {
            return b.getCheckOut().plusDays(COMPLAINT_WINDOW_DAYS);
        }
        LocalDate created = b.getCreatedAt() == null ? LocalDate.now()
                : LocalDate.ofInstant(b.getCreatedAt(), java.time.ZoneId.systemDefault());
        return created.plusDays(7);
    }

    public static String maskAccount(String no) {
        if (no == null || no.length() <= 4) return "****";
        return "****" + no.substring(no.length() - 4);
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
