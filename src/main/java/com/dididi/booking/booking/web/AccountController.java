package com.dididi.booking.booking.web;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.domain.enums.BookingType;
import com.dididi.booking.booking.service.BookingService;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.payment.domain.entity.Payment;
import com.dididi.booking.payment.service.PaymentService;
import com.dididi.booking.review.domain.entity.Review;
import com.dididi.booking.review.domain.enums.ReviewImageKind;
import com.dididi.booking.review.service.ReviewImageService;
import com.dididi.booking.review.service.ReviewService;
import com.dididi.booking.web.CurrentUser;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;

@Controller
public class AccountController {

    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final ReviewService reviewService;
    private final ReviewImageService reviewImageService;
    private final CurrentUser currentUser;
    private final com.dididi.booking.approval.service.ApprovalService approvalService;

    public AccountController(BookingService bookingService, PaymentService paymentService,
                             ReviewService reviewService, ReviewImageService reviewImageService,
                             CurrentUser currentUser,
                             com.dididi.booking.approval.service.ApprovalService approvalService) {
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        this.reviewService = reviewService;
        this.reviewImageService = reviewImageService;
        this.currentUser = currentUser;
        this.approvalService = approvalService;
    }

    @GetMapping("/account/bookings")
    public String myBookings(@RequestParam(required = false) String type,
                             @RequestParam(required = false) String status,
                             Authentication auth, Model model) {
        BookingType ft = parseEnum(BookingType.class, type);
        BookingStatus st = parseEnum(BookingStatus.class, status);
        model.addAttribute("bookings", bookingService.myBookings(currentUser.id(auth), ft, st));
        model.addAttribute("filterType", ft != null ? ft.name() : null);
        model.addAttribute("filterStatus", st != null ? st.name() : null);
        return "account/bookings";
    }

    /** Parse chuoi -> enum an toan (rong/sai -> null = khong loc). */
    private static <E extends Enum<E>> E parseEnum(Class<E> cls, String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return Enum.valueOf(cls, v.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
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
        Review myReview = reviewService.reviewForBooking(b.getId()).orElse(null);
        model.addAttribute("myReview", myReview);
        if (myReview != null) {
            model.addAttribute("myReviewImages", reviewImageService.listUrls(myReview.getId(), ReviewImageKind.REVIEW));
            model.addAttribute("myReplyImages", reviewImageService.listUrls(myReview.getId(), ReviewImageKind.REPLY));
        }
        model.addAttribute("pendingApproval", approvalService.isPendingApproval(b.getId()));
        // Nhom 1+2: chi cho tu huy khi con > 48h truoc nhan phong/khoi hanh.
        model.addAttribute("cancelAllowed", bookingService.withinCancelWindow(b));
        model.addAttribute("cancelDeadline", bookingService.cancelDeadline(b));
        Object tripCity = session.getAttribute("tripCity");
        if (tripCity != null) {
            model.addAttribute("tripCity", tripCity);
            model.addAttribute("tripAirport", session.getAttribute("tripAirport"));
        }
        return "account/booking-detail";
    }

    @PostMapping("/account/bookings/{code}/cancel")
    public String cancel(@PathVariable String code, @RequestParam(required = false) String reason,
                         Authentication auth, RedirectAttributes ra) {
        try {
            bookingService.requestCancel(code, currentUser.id(auth), reason);
            ra.addFlashAttribute("message",
                    "Đã gửi yêu cầu huỷ đơn " + code + ". Vui lòng chờ admin duyệt.");
        } catch (BusinessException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/account/bookings/" + code;
    }

    @PostMapping("/account/bookings/{code}/edit")
    public String edit(@PathVariable String code,
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
            ra.addFlashAttribute("message", "Đã cập nhật đơn. Vui lòng kiểm tra lại số tiền rồi thanh toán.");
        } catch (BusinessException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/account/bookings/" + code;
    }

    @PostMapping("/account/bookings/{code}/review")
    public String review(@PathVariable String code, @RequestParam int rating,
                         @RequestParam(required = false) String comment,
                         @RequestParam(value = "images", required = false) MultipartFile[] images,
                         Authentication auth, RedirectAttributes ra) {
        try {
            Review r = reviewService.create(currentUser.id(auth), code, rating, comment);
            if (images != null && images.length > 0) {
                reviewImageService.attachReviewImages(r.getId(), currentUser.id(auth), images);
            }
            ra.addFlashAttribute("message", "Cảm ơn bạn đã đánh giá!");
        } catch (BusinessException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/account/bookings/" + code;
    }
}
