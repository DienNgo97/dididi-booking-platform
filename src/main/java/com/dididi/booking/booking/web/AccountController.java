package com.dididi.booking.booking.web;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.service.BookingService;
import com.dididi.booking.payment.domain.entity.Payment;
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
public class AccountController {

    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final CurrentUser currentUser;

    public AccountController(BookingService bookingService, PaymentService paymentService,
                             CurrentUser currentUser) {
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        this.currentUser = currentUser;
    }

    @GetMapping("/account/bookings")
    public String myBookings(Authentication auth, Model model) {
        model.addAttribute("bookings", bookingService.myBookings(currentUser.id(auth)));
        return "account/bookings";
    }

    @GetMapping("/account/bookings/{code}")
    public String detail(@PathVariable String code, Authentication auth, Model model) {
        Booking b = bookingService.getForUser(code, currentUser.id(auth));
        Payment p = paymentService.findByBooking(b.getId()).orElse(null);
        model.addAttribute("booking", b);
        model.addAttribute("payment", p);
        return "account/booking-detail";
    }

    @PostMapping("/account/bookings/{code}/cancel")
    public String cancel(@PathVariable String code, Authentication auth, RedirectAttributes ra) {
        bookingService.cancel(code, currentUser.id(auth));
        ra.addFlashAttribute("message", "Đã huỷ đơn " + code);
        return "redirect:/account/bookings";
    }
}
