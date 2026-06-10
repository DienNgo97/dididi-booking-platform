package com.dididi.booking.booking.web;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.service.BookingService;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.payment.domain.entity.Payment;
import com.dididi.booking.payment.service.PaymentService;
import com.dididi.booking.review.service.ReviewService;
import com.dididi.booking.web.CurrentUser;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AccountController {

    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final ReviewService reviewService;
    private final CurrentUser currentUser;
    private final com.dididi.booking.approval.service.ApprovalService approvalService;

    public AccountController(BookingService bookingService, PaymentService paymentService,
                             ReviewService reviewService, CurrentUser currentUser,
                             com.dididi.booking.approval.service.ApprovalService approvalService) {
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        this.reviewService = reviewService;
        this.currentUser = currentUser;
        this.approvalService = approvalService;
    }

    @GetMapping("/account/bookings")
    public String myBookings(Authentication auth, Model model) {
        model.addAttribute("bookings", bookingService.myBookings(currentUser.id(auth)));
        return "account/bookings";
    }

    @GetMapping("/account/bookings/{code}")
    public String detail(@PathVariable String code,
                         @RequestParam(required = false) String endTrip,
                         Authentication auth, Model model, HttpSession session) {
        if (endTrip != null) {
            session.removeAttribute("tripCity");
            session.removeAttribute("tripAirport");
        }
        Booking b = bookingService.getForUser(code, currentUser.id(auth));
        Payment p = paymentService.findByBooking(b.getId()).orElse(null);
        model.addAttribute("booking", b);
        model.addAttribute("payment", p);
        model.addAttribute("myReview", reviewService.reviewForBooking(b.getId()).orElse(null));
        model.addAttribute("pendingApproval", approvalService.isPendingApproval(b.getId()));
        Object tripCity = session.getAttribute("tripCity");
        if (tripCity != null) {
            model.addAttribute("tripCity", tripCity);
            model.addAttribute("tripAirport", session.getAttribute("tripAirport"));
        }
        return "account/booking-detail";
    }

    @PostMapping("/account/bookings/{code}/cancel")
    public String cancel(@PathVariable String code, Authentication auth, RedirectAttributes ra) {
        bookingService.cancel(code, currentUser.id(auth));
        ra.addFlashAttribute("message", "Đã huỷ đơn " + code);
        return "redirect:/account/bookings";
    }

    @PostMapping("/account/bookings/{code}/review")
    public String review(@PathVariable String code, @RequestParam int rating,
                         @RequestParam(required = false) String comment,
                         Authentication auth, RedirectAttributes ra) {
        try {
            reviewService.create(currentUser.id(auth), code, rating, comment);
            ra.addFlashAttribute("message", "Cảm ơn bạn đã đánh giá!");
        } catch (BusinessException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/account/bookings/" + code;
    }
}
