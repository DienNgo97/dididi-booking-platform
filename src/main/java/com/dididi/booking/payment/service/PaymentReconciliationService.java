package com.dididi.booking.payment.service;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.booking.service.BookingService;
import com.dididi.booking.payment.domain.entity.Payment;
import com.dididi.booking.payment.domain.enums.PaymentStatus;
import com.dididi.booking.payment.repository.PaymentRepository;
import com.dididi.booking.payment.vnpay.VnPayQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * ĐỐI SOÁT CHỦ ĐỘNG với VNPay — lớp bảo vệ thứ ba cho việc xác nhận thanh toán.
 *
 * Vấn đề có thật (đo được ngày 16/08/2026): đơn chỉ chuyển sang CONFIRMED khi VNPay báo về,
 * mà cả hai đường báo đều có thể trượt:
 *   1. Trình duyệt quay lại (vnp_ReturnUrl) — mất nếu khách tắt tab / rớt mạng ngay sau khi trả tiền.
 *   2. IPN server-to-server — VNPay không gọi vào được khi hệ thống chạy localhost hoặc sau NAT.
 * Khi cả hai trượt: khách BỊ TRỪ TIỀN nhưng đơn kẹt ở PENDING_PAYMENT, rồi sau 20 phút
 * bị scheduler hết hạn giết luôn. Đây là loại lỗi tệ nhất — mất tiền của khách và không để lại dấu vết.
 *
 * Cách chữa: tự mình GỌI RA hỏi VNPay (querydr) thay vì ngồi chờ VNPay gọi vào.
 * Chiều gọi ra luôn thông, kể cả localhost — nên cách này hoạt động ở mọi môi trường,
 * không phải cấu hình lại gì khi đưa lên production.
 *
 * Vẫn giữ nguyên guard tiền của BP-PAY-01: chỉ xác nhận khi SỐ TIỀN VNPay báo khớp số tiền đơn.
 */
@Service
public class PaymentReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** Chờ ngần này phút rồi mới hỏi — tránh hỏi lúc khách còn đang gõ OTP. */
    private static final int MIN_AGE_MINUTES = 3;
    /**
     * Khoảng cách tối thiểu giữa hai lần hỏi VỀ CÙNG một giao dịch.
     * VNPay chặn yêu cầu trùng trong 5 phút (mã 94 "Request is duplicated"), nên hỏi dày hơn
     * mức đó là tự chuốc lấy lỗi — đo được thực tế ngày 17/08: quét 2 phút/lần thì MỌI phản hồi
     * đều là 94, không bao giờ lấy được kết quả thật.
     */
    private static final Duration MIN_GAP_PER_TXN = Duration.ofMinutes(6);

    /** txnRef -> lần cuối đã hỏi VNPay. Chỉ nằm trong bộ nhớ: khởi động lại thì hỏi lại, vô hại. */
    private final java.util.Map<String, Instant> lastAsked = new java.util.concurrent.ConcurrentHashMap<>();
    /** Quá ngần này thì thôi, khỏi hỏi nữa (VNPay cũng không giữ vô hạn). */
    private static final int MAX_AGE_HOURS = 24;

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final PaymentService paymentService;
    private final BookingService bookingService;
    private final VnPayQueryService queryService;
    private final boolean enabled;

    /**
     * Chinh no, nhung la BAN PROXY do Spring boc. Goi self.reconcile(...) thay vi this.reconcile(...)
     * de @Transactional thuc su chay. @Lazy de tranh vong phu thuoc luc khoi tao.
     */
    private PaymentReconciliationService self;

    @org.springframework.beans.factory.annotation.Autowired
    void setSelf(@org.springframework.context.annotation.Lazy PaymentReconciliationService self) {
        this.self = self;
    }

    public PaymentReconciliationService(PaymentRepository paymentRepository,
                                        BookingRepository bookingRepository,
                                        PaymentService paymentService,
                                        BookingService bookingService,
                                        VnPayQueryService queryService,
                                        @Value("${app.vnpay.reconcile-enabled:true}") boolean enabled) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.paymentService = paymentService;
        this.bookingService = bookingService;
        this.queryService = queryService;
        this.enabled = enabled;
    }

    /**
     * Quét toàn bộ giao dịch treo. ĐƯỢC GỌI TỪ {@link PaymentReconciliationScheduler} —
     * cố ý đặt @Scheduled ở bean KHÁC chứ không phải ở đây: nếu để chung một bean,
     * lời gọi {@code this.reconcile(p)} bên dưới sẽ đi thẳng, không qua proxy của Spring,
     * và @Transactional trên reconcile() sẽ MẤT TÁC DỤNG (đúng lỗi BP-INT-01 từng gặp).
     */
    public void sweep() {
        if (!enabled) return;
        Instant notAfter = Instant.now().minus(Duration.ofMinutes(MIN_AGE_MINUTES));
        Instant notBefore = Instant.now().minus(Duration.ofHours(MAX_AGE_HOURS));
        List<Payment> pending = paymentRepository
                .findByStatusAndMethodAndCreatedAtBetween(PaymentStatus.PENDING, "VNPAY", notBefore, notAfter);
        if (pending.isEmpty()) return;

        int confirmed = 0, failed = 0;
        for (Payment p : pending) {
            try {
                Outcome o = self.reconcile(p);   // qua proxy -> @Transactional co hieu luc
                if (o == Outcome.CONFIRMED) confirmed++;
                else if (o == Outcome.FAILED) failed++;
            } catch (Exception ex) {
                // Một đơn lỗi không được chặn các đơn còn lại.
                log.warn("[Đối soát] lỗi khi xử lý {}: {}", p.getTransactionRef(), ex.toString());
            }
        }
        lastAsked.entrySet().removeIf(e -> e.getValue().isBefore(Instant.now().minus(Duration.ofHours(2))));
        if (confirmed > 0 || failed > 0) {
            log.info("[Đối soát VNPay] quét {} giao dịch treo -> xác nhận {}, đánh dấu thất bại {}",
                    pending.size(), confirmed, failed);
        }
    }

    /**
     * CONFIRMED / FAILED: đã hỏi được VNPay và biết chắc kết quả.
     * UNKNOWN: hỏi được, nhưng giao dịch chưa ngã ngũ (hoặc VNPay báo chưa từng có giao dịch này).
     * UNREACHABLE: KHÔNG hỏi được (cổng lỗi, timeout, bị chặn vì hỏi trùng, chữ ký sai).
     *
     * Vì sao phải tách UNREACHABLE khỏi UNKNOWN: bên gọi cần phân biệt "đã hỏi và biết chắc khách
     * chưa trả tiền" với "chưa hỏi được nên không biết gì". Gộp chung hai thứ này chính là cái bẫy
     * đã làm đơn DD-543D3F bị huỷ lúc 10:04 ngày 17/08 trong khi VNPay đang từ chối trả lời —
     * đúng loại lỗi (giết đơn có thể đã trả tiền) mà lớp đối soát này sinh ra để chặn.
     */
    public enum Outcome {
        /** VNPay xác nhận đã thu tiền. */
        CONFIRMED,
        /** VNPay xác nhận giao dịch hỏng/huỷ. */
        FAILED,
        /** Không có giao dịch nào (khách chưa từng bấm trả) hoặc số tiền lệch — xử lý như cũ. */
        UNKNOWN,
        /** Không HỎI được VNPay (cổng lỗi/chặn/chữ ký sai) — "chưa biết", tuyệt đối không suy ra chưa trả. */
        UNREACHABLE,
        /** Khách ĐANG thao tác trả tiền (mã 01/04) — không được huỷ đơn lúc này (P0-3). */
        IN_PROGRESS
    }

    /**
     * Hỏi VNPay về một giao dịch rồi cập nhật cho đúng.
     * Idempotent: gọi lại nhiều lần không cộng điểm hay gửi mail trùng (nhờ markConfirmed).
     */
    @Transactional
    public Outcome reconcile(Payment p) {
        if (!enabled || p.getStatus() != PaymentStatus.PENDING) return Outcome.UNKNOWN;

        String txnRef = p.getTransactionRef();
        if (txnRef == null || txnRef.isBlank()) return Outcome.UNKNOWN;

        // Hỏi lại quá sớm thì VNPay chặn (94) — với bên gọi thì coi như KHÔNG hỏi được,
        // tuyệt đối không được hiểu nhầm thành "khách chưa trả tiền".
        Instant last = lastAsked.get(txnRef);
        if (last != null && last.isAfter(Instant.now().minus(MIN_GAP_PER_TXN))) return Outcome.UNREACHABLE;

        lastAsked.put(txnRef, Instant.now());
        VnPayQueryService.QueryResult r =
                queryService.query(txnRef, transactionDateOf(p), "127.0.0.1");

        if (!r.ok()) return Outcome.UNREACHABLE;             // cổng lỗi / bị chặn / chữ ký sai -> chưa biết gì

        if (r.notFound()) {
            // Khách chưa từng bấm trả tiền. Không đụng gì, để scheduler hết hạn xử lý như cũ.
            return Outcome.UNKNOWN;
        }

        if (r.paid()) {
            // GUARD TIỀN (BP-PAY-01): tuyệt đối không xác nhận khi số tiền lệch.
            if (r.amount() == null || p.getAmount() == null
                    || r.amount().compareTo(p.getAmount()) != 0) {
                log.error("[Đối soát] LỆCH SỐ TIỀN cho {}: VNPay báo {} nhưng đơn là {} — KHÔNG xác nhận, cần kiểm tra tay",
                        txnRef, r.amount(), p.getAmount());
                return Outcome.UNKNOWN;
            }
            paymentService.markPaid(p, r.transactionNo(), r.bankCode(), r.responseCode(), r.payDate());
            Booking b = bookingRepository.findById(p.getBookingId()).orElse(null);
            if (b != null && b.getStatus() == BookingStatus.PENDING_PAYMENT) {
                bookingService.markConfirmed(b);
                log.info("[Đối soát] {} đã thanh toán tại VNPay nhưng chưa được ghi nhận -> đã xác nhận đơn {}",
                        txnRef, b.getPublicCode());
            }
            return Outcome.CONFIRMED;
        }

        if (r.failed()) {
            paymentService.markFailed(p, r.responseCode());
            return Outcome.FAILED;
        }

        // "01 - giao dịch chưa hoàn tất" / "04 - giao dịch đảo": KHÁCH ĐANG THAO TÁC (nhập OTP...).
        // P0-3: trước đây trả UNKNOWN nên scheduler huỷ đơn ngay giữa lúc khách đang trả tiền
        // -> khách mất tiền, đơn FAILED, phòng bán cho người khác. Giờ tách riêng để hoãn huỷ.
        return Outcome.IN_PROGRESS;
    }

    /**
     * vnp_TransactionDate phải đúng bằng vnp_CreateDate lúc tạo giao dịch.
     * txnRef được sinh theo dạng {@code MÃ-ĐƠN_yyyyMMddHHmmss} nên lấy được ngay từ đó;
     * nếu vì lý do gì mà không tách được thì suy ra từ thời điểm tạo bản ghi.
     */
    private String transactionDateOf(Payment p) {
        String ref = p.getTransactionRef();
        int i = (ref == null) ? -1 : ref.lastIndexOf('_');
        if (i > 0 && ref.length() - i - 1 == 14) {
            String suffix = ref.substring(i + 1);
            if (suffix.chars().allMatch(Character::isDigit)) return suffix;
        }
        Instant created = p.getCreatedAt() != null ? p.getCreatedAt() : Instant.now();
        return LocalDateTime.ofInstant(created, ZONE).format(FMT);
    }

    /** Dùng cho hiển thị/kiểm thử: số tiền VNPay đang ghi nhận cho một giao dịch. */
    public BigDecimal amountAtGateway(Payment p) {
        VnPayQueryService.QueryResult r = queryService.query(p.getTransactionRef(), transactionDateOf(p), "127.0.0.1");
        return r.ok() ? r.amount() : null;
    }
}
