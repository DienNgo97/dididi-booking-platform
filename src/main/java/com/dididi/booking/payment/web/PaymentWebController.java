package com.dididi.booking.payment.web;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.booking.service.BookingService;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.corporate.api.dto.CompanyDto;
import com.dididi.booking.corporate.service.CompanyService;
import com.dididi.booking.corporate.service.CorporateBookingService;
import com.dididi.booking.payment.domain.entity.Payment;
import com.dididi.booking.payment.domain.entity.PaymentAttempt;
import com.dididi.booking.payment.domain.enums.PaymentStatus;
import com.dididi.booking.payment.repository.PaymentAttemptRepository;
import com.dididi.booking.payment.service.PaymentService;
import com.dididi.booking.payment.vnpay.VnPayService;
import com.dididi.booking.web.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Luong thanh toan VNPay sandbox (Phase 8a).
 *  - GET  /payment/{code}        : trang xac nhan thanh toan
 *  - POST /payment/{code}        : tao giao dich PENDING + redirect sang VNPay
 *  - GET  /payment/vnpay-return  : VNPay dieu huong trinh duyet ve -> verify + cap nhat + ve trang don
 *  - GET  /payment/vnpay-ipn     : VNPay goi server-to-server -> verify + cap nhat (idempotent), tra JSON
 */
@Controller
public class PaymentWebController {

    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final PaymentService paymentService;
    private final VnPayService vnPayService;
    private final CurrentUser currentUser;
    private final CompanyService companyService;
    private final CorporateBookingService corporateBookingService;
    private final com.dididi.booking.voucher.service.VoucherService voucherService;
    private final com.dididi.booking.group.service.GroupBookingService groupService;
    private final PaymentAttemptRepository paymentAttemptRepository;

    public PaymentWebController(BookingService bookingService, BookingRepository bookingRepository,
                                PaymentService paymentService, VnPayService vnPayService,
                                CurrentUser currentUser, CompanyService companyService,
                                CorporateBookingService corporateBookingService,
                                com.dididi.booking.voucher.service.VoucherService voucherService,
                                com.dididi.booking.group.service.GroupBookingService groupService,
                                PaymentAttemptRepository paymentAttemptRepository) {
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
        this.paymentService = paymentService;
        this.vnPayService = vnPayService;
        this.currentUser = currentUser;
        this.companyService = companyService;
        this.corporateBookingService = corporateBookingService;
        this.voucherService = voucherService;
        this.groupService = groupService;
        this.paymentAttemptRepository = paymentAttemptRepository;
    }

    /** Ghi nhật ký 1 lần VNPay gọi về (return/IPN) để đối soát. Lỗi ghi log KHÔNG được chặn thanh toán. */
    private void recordAttempt(String direction, Map<String, String> params, boolean signatureValid) {
        try {
            String txnRef = params.get("vnp_TxnRef");
            PaymentAttempt a = new PaymentAttempt();
            a.setDirection(direction);
            a.setTxnRef(txnRef);
            a.setResponseCode(params.get("vnp_ResponseCode"));
            a.setSignatureValid(signatureValid);
            try {
                String amt = params.get("vnp_Amount");
                if (amt != null) a.setAmount(new BigDecimal(amt).movePointLeft(2));
            } catch (Exception ignore) { }
            paymentService.findByTxnRef(txnRef).ifPresent(p -> { a.setPaymentId(p.getId()); a.setBookingId(p.getBookingId()); });
            StringBuilder sb = new StringBuilder();
            params.forEach((k, v) -> {
                if (k == null || k.toLowerCase().contains("securehash")) return;
                if (sb.length() > 0) sb.append('&');
                sb.append(k).append('=').append(v);
            });
            String raw = sb.toString();
            a.setRawParams(raw.length() > 2000 ? raw.substring(0, 2000) : raw);
            paymentAttemptRepository.save(a);
        } catch (Exception ignore) { /* ghi attempt lỗi không được chặn luồng thanh toán */ }
    }

    @GetMapping("/payment/{code}")
    public String payPage(@PathVariable String code, Authentication auth, Model model, RedirectAttributes ra) {
        Booking b = bookingService.getForUserOrGroupOrganizer(code, currentUser.id(auth));
        if (b.getStatus() == BookingStatus.CONFIRMED) {
            return "redirect:/account/bookings/" + code;            // da thanh toan roi
        }
        if (b.getStatus() != BookingStatus.PENDING_PAYMENT) {
            ra.addFlashAttribute("error", "Đơn này không còn ở trạng thái chờ thanh toán.");
            return "redirect:/account/bookings";
        }
        if (bookingService.isPaymentExpired(b)) {                   // qua 20' -> het han
            bookingService.markPaymentExpired(b);
            ra.addFlashAttribute("error", "Thời gian thanh toán đã hết hạn, vui lòng chọn lại phòng.");
            return "redirect:/";
        }
        model.addAttribute("booking", b);
        model.addAttribute("remainingSeconds", bookingService.remainingHoldSeconds(b));
        companyService.forUser(currentUser.id(auth))
                .ifPresent(c -> model.addAttribute("company", CompanyDto.from(c)));
        return "payment/pay";
    }

    /** Ap ma giam gia cho don. */
    @PostMapping("/payment/{code}/voucher")
    public String applyVoucher(@PathVariable String code, @RequestParam String voucherCode,
                               Authentication auth, RedirectAttributes ra) {
        try {
            Booking b = bookingService.getForUser(code, currentUser.id(auth));
            voucherService.apply(voucherCode, b, currentUser.id(auth));
            ra.addFlashAttribute("message", "Đã áp dụng mã giảm giá.");
        } catch (BusinessException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/payment/" + code;
    }

    /** Go ma giam gia khoi don. */
    @PostMapping("/payment/{code}/voucher/remove")
    public String removeVoucher(@PathVariable String code, Authentication auth, RedirectAttributes ra) {
        Booking b = bookingService.getForUser(code, currentUser.id(auth));
        voucherService.remove(b);
        ra.addFlashAttribute("message", "Đã gỡ mã giảm giá.");
        return "redirect:/payment/" + code;
    }

    /** Sua thong tin dat phong (don khach san DIRECT) ngay tai trang thanh toan -> quay lai trang thanh toan. */
    @PostMapping("/payment/{code}/edit")
    public String editBooking(@PathVariable String code,
                              @RequestParam(defaultValue = "false") boolean dayUse,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                              @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime timeIn,
                              @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime timeOut,
                              @RequestParam(defaultValue = "1") int rooms,
                              Authentication auth, RedirectAttributes ra) {
        try {
            if (dayUse) {
                bookingService.editDirectDayUse(code, currentUser.id(auth), date, timeIn, timeOut, rooms);
            } else {
                bookingService.editDirectOvernight(code, currentUser.id(auth), checkIn, checkOut, rooms);
            }
            ra.addFlashAttribute("message", "Đã cập nhật thông tin đặt phòng. Vui lòng kiểm tra lại số tiền.");
        } catch (BusinessException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/payment/" + code;
    }

    /** Thanh toan bang ngan sach cong ty (B2B) - khong qua VNPay. Het han muc -> chan + bao loi. */
    @PostMapping("/payment/{code}/company")
    public String payByCompany(@PathVariable String code, Authentication auth, RedirectAttributes ra) {
        Booking b = bookingService.getForUser(code, currentUser.id(auth));
        if (bookingService.isPaymentExpired(b)) {
            bookingService.markPaymentExpired(b);
            ra.addFlashAttribute("error", "Thời gian thanh toán đã hết hạn, vui lòng chọn lại phòng.");
            return "redirect:/";
        }
        try {
            com.dididi.booking.corporate.service.CorporatePaymentOutcome outcome =
                    corporateBookingService.payWithCompanyBudget(code, currentUser.id(auth));
            if (outcome == com.dididi.booking.corporate.service.CorporatePaymentOutcome.PENDING_APPROVAL) {
                ra.addFlashAttribute("message",
                        "Đã gửi yêu cầu phê duyệt (đơn vượt ngưỡng duyệt của công ty). Đơn sẽ được xác nhận sau khi được duyệt.");
            } else {
                ra.addFlashAttribute("message", "Đã thanh toán bằng ngân sách công ty. Đơn đã được xác nhận.");
            }
            return "redirect:/account/bookings/" + code;
        } catch (BusinessException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/payment/" + code;
        }
    }

    /** Tao giao dich PENDING roi chuyen huong sang cong VNPay. */
    @PostMapping("/payment/{code}")
    public String startVnpay(@PathVariable String code, Authentication auth, HttpServletRequest req,
                             RedirectAttributes ra) {
        Booking b = bookingService.getForUserOrGroupOrganizer(code, currentUser.id(auth));
        if (bookingService.isPaymentExpired(b)) {
            bookingService.markPaymentExpired(b);
            ra.addFlashAttribute("error", "Thời gian thanh toán đã hết hạn, vui lòng chọn lại phòng.");
            return "redirect:/";
        }
        Payment p = paymentService.initiateVnpay(b);
        String url = vnPayService.createPaymentUrl(b, p.getTransactionRef(), clientIp(req));
        return "redirect:" + url;
    }

    /**
     * VNPay dieu huong trinh duyet nguoi dung ve day sau khi thanh toan.
     * BP-PAY-03: @Transactional + chi mutate khi Payment dang PENDING (markConfirmed da idempotent).
     * BP-PAY-01: coi return la UNTRUSTED — phai kiem tra vnp_Amount == payment.amount*100 truoc khi confirm
     * (giong IPN). Ap dung cho CA nhanh thuong lan nhanh tra-ca-nhom.
     */
    @GetMapping("/payment/vnpay-return")
    @Transactional
    public String vnpayReturn(@RequestParam Map<String, String> params, RedirectAttributes ra, HttpSession session) {
        String txnRef = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");
        String txnStatus = params.get("vnp_TransactionStatus");
        String publicCode = extractCode(txnRef);

        boolean validSig = vnPayService.isValid(params);
        recordAttempt("RETURN", params, validSig);
        if (!validSig) {
            ra.addFlashAttribute("error", "Chu ky VNPay khong hop le. Vui long thu lai.");
            return (publicCode == null || publicCode.isEmpty()) ? "redirect:/account/bookings"
                    : "redirect:/account/bookings/" + publicCode;
        }
        Optional<Payment> op = paymentService.findByTxnRef(txnRef);
        if (op.isEmpty()) {
            ra.addFlashAttribute("error", "Khong tim thay giao dich.");
            return "redirect:/account/bookings";
        }
        Payment p = op.get();
        boolean ok = "00".equals(responseCode) && "00".equals(txnStatus);

        // === Thanh toan GOP CA NHOM (txnRef = "GRP{groupId}_{ts}") ===
        if (txnRef != null && txnRef.startsWith("GRP")) {
            Long groupId = parseGroupId(txnRef);
            // BP-PAY-01: so tien tra phai khop so tien giao dich da khoi tao.
            if (ok && !amountMatches(p, params)) {
                String token = (groupId != null) ? groupService.tokenOf(groupId) : "";
                ra.addFlashAttribute("error", "Số tiền thanh toán không khớp giao dịch. Đơn KHÔNG được xác nhận.");
                return token.isEmpty() ? "redirect:/account/bookings" : "redirect:/g/" + token;
            }
            if (ok) {
                if (p.getStatus() == PaymentStatus.PENDING) {
                    paymentService.markPaid(p, params.get("vnp_TransactionNo"),
                            params.get("vnp_BankCode"), responseCode, params.get("vnp_PayDate"));
                }
                String token = (groupId != null) ? groupService.confirmGroupBookings(groupId) : "";
                ra.addFlashAttribute("message", "Thanh toan VNPay thanh cong! Da xac nhan cac phong da chon cua nhom.");
                return token.isEmpty() ? "redirect:/account/bookings" : "redirect:/g/" + token;
            } else {
                if (p.getStatus() == PaymentStatus.PENDING) {
                    paymentService.markFailed(p, responseCode);
                }
                String token = (groupId != null) ? groupService.tokenOf(groupId) : "";
                ra.addFlashAttribute("error", "Thanh toan khong thanh cong (ma " + responseCode + "). Ban co the thu lai.");
                return token.isEmpty() ? "redirect:/account/bookings" : "redirect:/g/" + token;
            }
        }

        Booking b = bookingRepository.findById(p.getBookingId()).orElse(null);
        // BP-PAY-01: kiem tra so tien truoc khi xac nhan don thuong.
        if (ok && !amountMatches(p, params)) {
            ra.addFlashAttribute("error", "Số tiền thanh toán không khớp giao dịch. Đơn KHÔNG được xác nhận.");
            String mcode = (b != null) ? b.getPublicCode() : publicCode;
            return (mcode == null || mcode.isEmpty()) ? "redirect:/account/bookings" : "redirect:/account/bookings/" + mcode;
        }
        if (ok) {
            if (p.getStatus() == PaymentStatus.PENDING) {
                paymentService.markPaid(p, params.get("vnp_TransactionNo"),
                        params.get("vnp_BankCode"), responseCode, params.get("vnp_PayDate"));
            }
            if (b != null && b.getStatus() == BookingStatus.PENDING_PAYMENT) {
                // Don thuoc nhom & tu tra le -> nguoi chi tien la chu phong (cho phieu chia tien nhom).
                if (b.getGroupId() != null && b.getPaidByUserId() == null) {
                    b.setPaidByUserId(b.getUserId());
                }
                bookingService.markConfirmed(b);
            }
            ra.addFlashAttribute("message", "Thanh toan VNPay thanh cong! Don da duoc xac nhan.");
        } else {
            if (p.getStatus() == PaymentStatus.PENDING) {
                paymentService.markFailed(p, responseCode);
            }
            ra.addFlashAttribute("error", "Thanh toan khong thanh cong (ma " + responseCode + "). Ban co the thu lai.");
        }
        String code = (b != null) ? b.getPublicCode() : publicCode;
        // Trong luong Trip Planner: tra xong 1 don -> tu dong chuyen sang don ke tiep (ve di -> ve ve -> khach san).
        if (ok && b != null && isTripBooking(session, b.getPublicCode())) {
            return "redirect:/trip-planner/pay-next";
        }
        return (code == null || code.isEmpty()) ? "redirect:/account/bookings" : "redirect:/account/bookings/" + code;
    }

    /** Don co nam trong gio "chuyen di" dang thanh toan tuan tu (luu trong session) khong? */
    @SuppressWarnings("unchecked")
    private static boolean isTripBooking(HttpSession session, String code) {
        Object raw = session.getAttribute("tripBookingCodes");
        return raw instanceof List && ((List<String>) raw).contains(code);
    }

    /** Tach groupId tu txnRef thanh toan gop ca nhom dang "GRP{groupId}_{ts}". */
    private static Long parseGroupId(String txnRef) {
        try {
            int us = txnRef.indexOf('_');
            String num = (us > 3) ? txnRef.substring(3, us) : txnRef.substring(3);
            return Long.parseLong(num);
        } catch (Exception e) {
            return null;
        }
    }

    /** IPN server-to-server tu VNPay. Can URL public (ngrok) khi cau hinh ben merchant. */
    @GetMapping("/payment/vnpay-ipn")
    @ResponseBody
    @Transactional
    public Map<String, String> vnpayIpn(@RequestParam Map<String, String> params) {
        Map<String, String> res = new HashMap<>();
        boolean validSig = vnPayService.isValid(params);
        recordAttempt("IPN", params, validSig);
        if (!validSig) {
            res.put("RspCode", "97"); res.put("Message", "Invalid signature"); return res;
        }
        String txnRef = params.get("vnp_TxnRef");
        Optional<Payment> op = paymentService.findByTxnRef(txnRef);
        if (op.isEmpty()) {
            res.put("RspCode", "01"); res.put("Message", "Order not found"); return res;
        }
        Payment p = op.get();
        try {
            long expected = p.getAmount().multiply(BigDecimal.valueOf(100)).longValueExact();
            long got = Long.parseLong(params.getOrDefault("vnp_Amount", "-1"));
            if (expected != got) { res.put("RspCode", "04"); res.put("Message", "Invalid amount"); return res; }
        } catch (Exception ignore) { /* bo qua neu parse loi */ }

        if (p.getStatus() != PaymentStatus.PENDING) {
            res.put("RspCode", "02"); res.put("Message", "Order already confirmed"); return res;
        }
        boolean ok = "00".equals(params.get("vnp_ResponseCode")) && "00".equals(params.get("vnp_TransactionStatus"));
        if (ok) {
            paymentService.markPaid(p, params.get("vnp_TransactionNo"),
                    params.get("vnp_BankCode"), params.get("vnp_ResponseCode"), params.get("vnp_PayDate"));
            bookingRepository.findById(p.getBookingId()).ifPresent(b -> {
                if (b.getStatus() == BookingStatus.PENDING_PAYMENT) bookingService.markConfirmed(b);
            });
        } else {
            paymentService.markFailed(p, params.get("vnp_ResponseCode"));
        }
        res.put("RspCode", "00"); res.put("Message", "Confirm Success");
        return res;
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    private static String extractCode(String txnRef) {
        if (txnRef == null) return "";
        int i = txnRef.lastIndexOf('_');
        return i > 0 ? txnRef.substring(0, i) : txnRef;
    }

    /**
     * BP-PAY-01: so tien VNPay tra ve (vnp_Amount, don vi = VND*100) phai khop payment.amount*100.
     * Tra false neu lech hoac khong parse duoc -> coi nhu khong hop le, KHONG confirm.
     */
    private static boolean amountMatches(Payment p, Map<String, String> params) {
        try {
            if (p.getAmount() == null) return false;
            long expected = p.getAmount().multiply(BigDecimal.valueOf(100)).longValueExact();
            long got = Long.parseLong(params.getOrDefault("vnp_Amount", "-1"));
            return expected == got;
        } catch (Exception ex) {
            return false;   // fail-closed
        }
    }
}
