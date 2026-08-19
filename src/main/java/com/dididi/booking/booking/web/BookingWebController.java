package com.dididi.booking.booking.web;

import com.dididi.booking.common.i18n.I18nSupport;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.service.BookingService;
import com.dididi.booking.flight.domain.entity.Flight;
import com.dididi.booking.flight.repository.FlightRepository;
import com.dididi.booking.integration.service.MockFlightProviderAdapter;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Controller
public class BookingWebController {

    private final BookingService bookingService;
    private final FlightRepository flightRepository;
    private final CurrentUser currentUser;
    private final MockFlightProviderAdapter flightAdapter;

    public BookingWebController(BookingService bookingService, FlightRepository flightRepository,
                                CurrentUser currentUser, MockFlightProviderAdapter flightAdapter) {
        this.bookingService = bookingService;
        this.flightRepository = flightRepository;
        this.currentUser = currentUser;
        this.flightAdapter = flightAdapter;
    }

    @GetMapping("/booking/flight/{flightId}")
    public String flightForm(@PathVariable Long flightId,
                             @RequestParam(required = false) String tripCity,
                             @RequestParam(required = false) String tripAirport,
                             @RequestParam(required = false) String tripLeg,
                             @RequestParam(required = false) String tripDest,
                             @RequestParam(required = false) String tripFrom,
                             @RequestParam(required = false) String tripDepart,
                             @RequestParam(required = false) String tripReturn,
                             @RequestParam(required = false) String cabin,
                             Authentication auth, Model model, HttpSession session) {
        Flight f = flightRepository.findById(flightId).orElse(null);
        if (f == null) return "redirect:/flights";
        if (tripCity != null && !tripCity.isBlank()) {
            session.setAttribute("tripCity", tripCity);
            session.setAttribute("tripAirport", tripAirport);
        }
        model.addAttribute("flight", f);
        // Chuyen da dong bo flight-provider -> lay so do ghe cho khach chon (provider loi thi bo qua, dung o so luong).
        // BP-BK-07: dung helper public cua BookingService thay vi hardcode 900000 -> 1 nguon su that.
        if (bookingService.isProviderFlight(f)) {
            try {
                var seatMap = flightAdapter.getSeatMap(f.getExternalId());
                model.addAttribute("seatMap", seatMap);
                // Tự chọn sẵn 1 ghế FREE đúng hạng khách đã tìm (cabin) để map nhu cầu — khách vẫn đổi được.
                if (cabin != null && !cabin.isBlank() && seatMap != null && seatMap.seats() != null) {
                    String want = cabin.trim();
                    seatMap.seats().stream()
                            .filter(s -> "FREE".equalsIgnoreCase(s.status()) && want.equalsIgnoreCase(s.seatClass()))
                            .findFirst()
                            .ifPresent(s -> {
                                model.addAttribute("preselectSeat", s.code());
                                model.addAttribute("preselectClass", s.seatClass());
                            });
                }
            } catch (Exception ex) {
                // khong lay duoc so do -> form dung o nhap so ghe nhu cu
            }
        }
        model.addAttribute("cabin", cabin);
        model.addAttribute("meals", com.dididi.booking.booking.FlightAddons.MEALS);
        model.addAttribute("bags", com.dididi.booking.booking.FlightAddons.BAGS);
        model.addAttribute("fullName", currentUser.require(auth).getFullName());
        model.addAttribute("tripLeg", tripLeg);
        model.addAttribute("tripDest", tripDest);
        model.addAttribute("tripFrom", tripFrom);
        model.addAttribute("tripDepart", tripDepart);
        model.addAttribute("tripReturn", tripReturn);
        return "booking/flight-form";
    }

    @PostMapping("/booking/flight")
    public String bookFlight(@RequestParam Long flightId,
                             @RequestParam(required = false) List<String> paxName,
                             @RequestParam(required = false) List<String> paxMeal,
                             @RequestParam(required = false) List<String> paxBag,
                             @RequestParam(required = false) String contactEmail,
                             @RequestParam(defaultValue = "1") int seats,
                             @RequestParam(required = false) String seatCodes,
                             @RequestParam(required = false) String tripLeg,
                             @RequestParam(required = false) String tripDest,
                             @RequestParam(required = false) String tripFrom,
                             @RequestParam(required = false) String tripDepart,
                             @RequestParam(required = false) String tripReturn,
                             Authentication auth, RedirectAttributes ra, HttpSession session) {
        try {
            // Ghế đã chọn (đơn có sơ đồ) -> gán cho từng hành khách theo thứ tự.
            List<String> codes = new ArrayList<>();
            if (seatCodes != null && !seatCodes.isBlank()) {
                for (String c : seatCodes.split(",")) { String t = c.trim(); if (!t.isEmpty()) codes.add(t); }
            }
            // Gom thông tin từng hành khách + TÍNH PHỤ PHÍ suất ăn/hành lý ở server (không tin giá client).
            StringBuilder pax = new StringBuilder();
            java.math.BigDecimal extras = java.math.BigDecimal.ZERO;
            String firstName = null;
            int n = (paxName == null) ? 0 : paxName.size();
            for (int i = 0; i < n; i++) {
                String name = (paxName.get(i) == null) ? "" : paxName.get(i).trim();
                if (name.isEmpty()) continue;
                if (firstName == null) firstName = name;
                String mealCode = paxAt(paxMeal, i), bagCode = paxAt(paxBag, i);
                extras = extras.add(java.math.BigDecimal.valueOf(
                        com.dididi.booking.booking.FlightAddons.mealPrice(mealCode)
                                + com.dididi.booking.booking.FlightAddons.bagPrice(bagCode)));
                if (pax.length() > 0) pax.append("\n");
                pax.append("• ").append(name);
                if (i < codes.size()) pax.append(" · ghế ").append(codes.get(i));
                pax.append(" · 🍽 ").append(com.dididi.booking.booking.FlightAddons.mealLabel(mealCode));
                pax.append(" · 🧳 ").append(com.dididi.booking.booking.FlightAddons.bagLabel(bagCode));
            }
            String passengersText = (pax.length() > 0) ? pax.toString() : null;
            String passengerName = (firstName != null) ? firstName : "Khách";
            int seatCount = !codes.isEmpty() ? codes.size() : (n > 0 ? n : seats);

            Booking b;
            if (!codes.isEmpty()) {
                b = bookingService.createFlightBookingWithSeats(currentUser.id(auth), flightId,
                        passengerName, contactEmail, codes, passengersText, extras);
            } else {
                b = bookingService.createFlightBooking(currentUser.id(auth), flightId,
                        passengerName, contactEmail, seatCount, passengersText, extras);
            }
            if (tripLeg != null && !tripLeg.isBlank()) {
                rememberTripBooking(session, b.getPublicCode());   // gom don de thanh toan o buoc checkout
                ra.addAttribute("dest", tripDest);
                ra.addAttribute("from", tripFrom);
                ra.addAttribute("depart", tripDepart);
                ra.addAttribute("ret", tripReturn);
                if ("outbound".equalsIgnoreCase(tripLeg)) {
                    ra.addFlashAttribute("message", I18nSupport.msg("flash.f41", "Đã đặt vé chiều đi (chờ thanh toán). Tiếp tục chọn vé chiều về."));
                    return "redirect:/trip-planner/inbound";
                }
                ra.addFlashAttribute("message", I18nSupport.msg("flash.f40", "Đã đặt vé chiều về (chờ thanh toán). Tiếp tục chọn khách sạn."));
                return "redirect:/trip-planner/stay";
            }
            return "redirect:/payment/" + b.getPublicCode();
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            if (tripLeg != null && !tripLeg.isBlank()) {   // giu ngu canh trip de form van con du lieu
                ra.addAttribute("tripLeg", tripLeg);
                ra.addAttribute("tripDest", tripDest);
                ra.addAttribute("tripFrom", tripFrom);
                ra.addAttribute("tripDepart", tripDepart);
                ra.addAttribute("tripReturn", tripReturn);
            }
            return "redirect:/booking/flight/" + flightId;
        }
    }

    /** Lấy phần tử thứ i của danh sách (null-safe) - dùng cho mảng paxMeal/paxBag. */
    private static String paxAt(List<String> l, int i) {
        return (l != null && i < l.size()) ? l.get(i) : null;
    }

    @PostMapping("/booking/hotel")
    public String bookHotel(@RequestParam Long hotelId,
                            @RequestParam Long roomTypeId,
                            @RequestParam(required = false) String roomName,
                            @RequestParam String guestName,
                            @RequestParam(defaultValue = "false") boolean dayUse,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime timeIn,
                            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime timeOut,
                            @RequestParam(defaultValue = "1") int rooms,
                            @RequestParam(required = false) String trip,
                            Authentication auth, RedirectAttributes ra, HttpSession session) {
        try {
            Booking b = dayUse
                    ? bookingService.createDayUseHotelBooking(currentUser.id(auth), hotelId, roomTypeId,
                            roomName, guestName, date, timeIn, timeOut, rooms)
                    : bookingService.createHotelBooking(currentUser.id(auth), hotelId, roomTypeId,
                            roomName, guestName, checkIn, checkOut, rooms);
            if ("1".equals(trip)) {   // dat trong luong Trip Planner -> bat dau chuoi thanh toan tuan tu
                rememberTripBooking(session, b.getPublicCode());
                return "redirect:/trip-planner/pay-next";
            }
            return "redirect:/payment/" + b.getPublicCode();
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            // Giu lai gia tri customer da nhap (loai cho o + ngay + gio + so phong) de chi can sua lai,
            // khong phai chon lai nut "trong ngay", nhap ngay/gio tu dau.
            ra.addAttribute("stay", dayUse ? "day" : "overnight");
            ra.addAttribute("keepRooms", rooms);
            // Serialize ISO bang .toString() (yyyy-MM-dd / HH:mm) — KHONG de locale vi doi thanh dd/MM/yyyy,
            // vi <input type="date"> chi nhan yyyy-MM-dd (sai dinh dang -> o trong -> JS dien hom nay).
            if (dayUse) {
                if (date != null) ra.addAttribute("date", date.toString());
                if (timeIn != null) ra.addAttribute("timeIn", timeIn.toString());
                if (timeOut != null) ra.addAttribute("timeOut", timeOut.toString());
            } else {
                if (checkIn != null) ra.addAttribute("checkIn", checkIn.toString());
                if (checkOut != null) ra.addAttribute("checkOut", checkOut.toString());
            }
            if ("1".equals(trip)) ra.addAttribute("trip", trip);   // giu co trip de form van o trong luong
            return "redirect:/hotels/" + hotelId;
        }
    }

    /** Gom ma don cua chuyen di vao session de buoc thanh toan tuan tu (/trip-planner/pay-next). */
    @SuppressWarnings("unchecked")
    private static void rememberTripBooking(HttpSession session, String code) {
        List<String> codes = (List<String>) session.getAttribute("tripBookingCodes");
        if (codes == null) codes = new ArrayList<>();
        if (!codes.contains(code)) codes.add(code);
        // PHAI set lai: session luu o Redis (spring-session) -> sua ArrayList tai cho khong duoc ghi nguoc lai.
        session.setAttribute("tripBookingCodes", codes);
    }
}
