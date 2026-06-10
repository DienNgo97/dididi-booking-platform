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
import com.dididi.booking.payment.domain.enums.PaymentStatus;
import com.dididi.booking.payment.service.PaymentService;
import com.dididi.booking.payment.vnpay.VnPayService;
import com.dididi.booking.web.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.HashMap;
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

    public PaymentWebController(BookingService bookingService, BookingRepository bookingRepository,
                                PaymentService paymentService, VnPayService vnPayService,
                                CurrentUser currentUser, CompanyService companyService,
                                CorporateBookingService corporateBookingService,
                                com.dididi.booking.voucher.service.VoucherService voucherService) {
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
        this.paymentService = paymentService;
        this.vnPayService = vnPayService;
        this.currentUser = currentUser;
        this.companyService = companyService;
        this.corporateBookingService = corporateBookingService;
        this.voucherService = voucherService;
    }

    @GetMapping("/payment/{code}")
    public String payPage(@PathVariable String code, Authentication auth, Model model) {
        Booking b = bookingService.getForUser(code, currentUser.id(auth));
        model.addAttribute("booking", b);
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

    /** Thanh toan bang ngan sach cong ty (B2B) - khong qua VNPay. Het han muc -> chan + bao loi. */
    @PostMapping("/payment/{code}/company")
    public String payByCompany(@PathVariable String code, Authentication auth, RedirectAttributes ra) {
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
    public String startVnpay(@PathVariable String code, Authentication auth, HttpServletRequest req) {
        Booking b = bookingService.getForUser(code, currentUser.id(auth));
        Payment p = paymentService.initiateVnpay(b);
        String url = vnPayService.createPaymentUrl(b, p.getTransactionRef(), clientIp(req));
        return "redirect:" + url;
    }

    /** VNPay dieu huong trinh duyet nguoi dung ve day sau khi thanh toan. */
    @GetMapping("/payment/vnpay-return")
    public String vnpayReturn(@RequestParam Map<String, String> params, RedirectAttributes ra) {
        String txnRef = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");
        String txnStatus = params.get("vnp_TransactionStatus");
        String publicCode = extractCode(txnRef);

        if (!vnPayService.isValid(params)) {
            ra.addFlashAttribute("error", "Chu ky VNPay khong hop le. Vui long thu lai.");
            return publicCode.isEmpty() ? "redirect:/account/bookings"
                    : "redirect:/account/bookings/" + publicCode;
        }
        Optional<Payment> op = paymentService.findByTxnRef(txnRef);
        if (op.isEmpty()) {
            ra.addFlashAttribute("error", "Khong tim thay giao dich.");
            return "redirect:/account/bookings";
        }
        Payment p = op.get();
        Booking b = bookingRepository.findById(p.getBookingId()).orElse(null);

        boolean ok = "00".equals(responseCode) && "00".equals(txnStatus);
        if (ok) {
            if (p.getStatus() == PaymentStatus.PENDING) {
                paymentService.markPaid(p, params.get("vnp_TransactionNo"),
                        params.get("vnp_BankCode"), responseCode, params.get("vnp_PayDate"));
            }
            if (b != null && b.getStatus() == BookingStatus.PENDING_PAYMENT) {
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
        return code.isEmpty() ? "redirect:/account/bookings" : "redirect:/account/bookings/" + code;
    }

    /** IPN server-to-server tu VNPay. Can URL public (ngrok) khi cau hinh ben merchant. */
    @GetMapping("/payment/vnpay-ipn")
    @ResponseBody
    public Map<String, String> vnpayIpn(@RequestParam Map<String, String> params) {
        Map<String, String> res = new HashMap<>();
        if (!vnPayService.isValid(params)) {
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
}
