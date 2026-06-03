package com.dididi.booking.payment.web;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.service.BookingService;
import com.dididi.booking.payment.service.PaymentService;
import com.dididi.booking.web.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PaymentWebController {

    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final CurrentUser currentUser;

    public PaymentWebController(BookingService bookingService, PaymentService paymentService,
                                CurrentUser currentUser) {
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        this.currentUser = currentUser;
    }

    @GetMapping("/payment/{code}")
    public String payPage(@PathVariable String code, Authentication auth, Model model) {
        Booking b = bookingService.getForUser(code, currentUser.id(auth));
        model.addAttribute("booking", b);
        return "payment/pay";
    }

    @PostMapping("/payment/{code}")
    public String pay(@PathVariable String code, Authentication auth, RedirectAttributes ra) {
        Booking b = bookingService.getForUser(code, currentUser.id(auth));
        paymentService.pay(b);
        bookingService.markConfirmed(b);
        ra.addFlashAttribute("message", "Thanh toán thành công! Đơn đã được xác nhận.");
        return "redirect:/account/bookings/" + b.getPublicCode();
    }
}
