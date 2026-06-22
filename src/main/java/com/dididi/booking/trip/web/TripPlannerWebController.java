package com.dididi.booking.trip.web;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.service.BookingService;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.trip.service.TripPlannerService;
import com.dididi.booking.web.CurrentUser;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Trip Planner: luong dat co huong dan.
 *  form (diem den + san bay di + ngay den + ngay ve)
 *   -> ve may bay CHIEU DI -> (dat) -> ve may bay CHIEU VE -> (dat) -> khach san (khoang o) -> (dat)
 *   -> CHECKOUT: liet ke ca 3 don + thanh toan tung don.
 * Ngu canh tim kiem (dest/from/depart/ret) giu qua query param; ma cac don giu trong session de checkout.
 */
@Controller
public class TripPlannerWebController {

    static final String TRIP_BOOKINGS = "tripBookingCodes";

    private final TripPlannerService tripPlannerService;
    private final BookingService bookingService;
    private final CurrentUser currentUser;
    private final HotelRepository hotelRepository;

    public TripPlannerWebController(TripPlannerService tripPlannerService,
                                    BookingService bookingService, CurrentUser currentUser,
                                    HotelRepository hotelRepository) {
        this.tripPlannerService = tripPlannerService;
        this.bookingService = bookingService;
        this.currentUser = currentUser;
        this.hotelRepository = hotelRepository;
    }

    @GetMapping("/trip-planner")
    public String form() {
        return "trip/planner";
    }

    /** Buoc 1/3: chuyen bay chieu di (from -> diem den) dung NGAY DEN. Bat dau chuyen moi -> reset gio hang don. */
    @GetMapping("/trip-planner/outbound")
    public String outbound(@RequestParam String dest,
                           @RequestParam String from,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate depart,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ret,
                           Model model, HttpSession session) {
        session.setAttribute(TRIP_BOOKINGS, new ArrayList<String>());   // chuyen moi -> bo cac don cu
        String destAirport = tripPlannerService.airportFor(dest);
        model.addAttribute("flights", tripPlannerService.availableFlights(from, destAirport, depart));
        fillContext(model, "outbound", dest, from, destAirport, depart, ret);
        model.addAttribute("routeFrom", from);
        model.addAttribute("routeTo", destAirport);
        model.addAttribute("flightDate", depart.toString());
        return "trip/flights";
    }

    /** Buoc 2/3: chuyen bay chieu ve (diem den -> from) dung NGAY VE. */
    @GetMapping("/trip-planner/inbound")
    public String inbound(@RequestParam String dest,
                          @RequestParam String from,
                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate depart,
                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ret,
                          Model model) {
        String destAirport = tripPlannerService.airportFor(dest);
        model.addAttribute("flights", tripPlannerService.availableFlights(destAirport, from, ret));
        fillContext(model, "return", dest, from, destAirport, depart, ret);
        model.addAttribute("routeFrom", destAirport);
        model.addAttribute("routeTo", from);
        model.addAttribute("flightDate", ret.toString());
        return "trip/flights";
    }

    /** Buoc 3/3: dat khach san cho khoang o tai diem den -> dung lai trang /hotels (giu thanh pho + ngay + co trip=1). */
    @GetMapping("/trip-planner/stay")
    public String stay(@RequestParam String dest,
                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate depart,
                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ret,
                       RedirectAttributes ra) {
        // Không có khách sạn nào ở điểm đến -> bỏ qua bước khách sạn, sang thẳng thanh toán vé máy bay (nếu có).
        if (hotelRepository.searchActiveByKeyword(dest).isEmpty()) {
            ra.addFlashAttribute("message",
                    "Không tìm thấy khách sạn ở \"" + dest + "\". Mời bạn thanh toán vé máy bay đã đặt.");
            return "redirect:/trip-planner/checkout";
        }
        ra.addAttribute("city", dest);
        ra.addAttribute("checkIn", depart.toString());
        ra.addAttribute("checkOut", ret.toString());
        ra.addAttribute("stay", "overnight");
        ra.addAttribute("trip", "1");
        return "redirect:/hotels";
    }

    /** Checkout: liet ke cac don da dat trong chuyen (ve di + ve ve + khach san) de thanh toan tung don. */
    @GetMapping("/trip-planner/checkout")
    public String checkout(HttpSession session, Authentication auth, Model model) {
        Long userId = currentUser.idOrNull(auth);
        Object raw = session.getAttribute(TRIP_BOOKINGS);
        List<Booking> bookings = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        boolean anyPending = false;
        if (userId != null && raw instanceof List) {
            for (Object code : (List<?>) raw) {
                try {
                    Booking b = bookingService.getForUser(String.valueOf(code), userId);
                    bookings.add(b);
                    if (b.getStatus() == BookingStatus.PENDING_PAYMENT) {
                        anyPending = true;
                        if (b.getAmount() != null) total = total.add(b.getAmount());
                    }
                } catch (Exception ignored) {
                    // don khong con/khong thuoc user -> bo qua
                }
            }
        }
        model.addAttribute("bookings", bookings);
        model.addAttribute("total", total);
        model.addAttribute("anyPending", anyPending);
        return "trip/checkout";
    }

    /**
     * Thanh toan tuan tu: chuyen toi don DAU TIEN con cho thanh toan trong chuyen (theo thu tu da dat).
     * Het don chua tra -> xong chuyen -> ve Don cua toi.
     */
    @GetMapping("/trip-planner/pay-next")
    public String payNext(HttpSession session, Authentication auth, RedirectAttributes ra) {
        Long userId = currentUser.idOrNull(auth);
        Object raw = session.getAttribute(TRIP_BOOKINGS);
        if (userId != null && raw instanceof List) {
            for (Object code : (List<?>) raw) {
                try {
                    Booking b = bookingService.getForUser(String.valueOf(code), userId);
                    if (b.getStatus() == BookingStatus.PENDING_PAYMENT) {
                        return "redirect:/payment/" + b.getPublicCode();   // tra don dau tien con cho
                    }
                } catch (Exception ignored) {
                    // don khong con/khong thuoc user -> bo qua, xet don tiep theo
                }
            }
        }
        session.removeAttribute(TRIP_BOOKINGS);                              // het don -> ket thuc chuyen
        ra.addFlashAttribute("message", "Đã thanh toán xong toàn bộ chuyến đi. Cảm ơn bạn!");
        return "redirect:/account/bookings";
    }

    private void fillContext(Model m, String leg, String dest, String from, String destAirport,
                             LocalDate depart, LocalDate ret) {
        m.addAttribute("leg", leg);
        m.addAttribute("dest", dest);
        m.addAttribute("from", from);
        m.addAttribute("destAirport", destAirport);
        m.addAttribute("depart", depart.toString());
        m.addAttribute("ret", ret.toString());
    }
}
