package com.dididi.booking.booking.web;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.service.BookingService;
import com.dididi.booking.flight.domain.entity.Flight;
import com.dididi.booking.flight.repository.FlightRepository;
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

@Controller
public class BookingWebController {

    private final BookingService bookingService;
    private final FlightRepository flightRepository;
    private final CurrentUser currentUser;

    public BookingWebController(BookingService bookingService, FlightRepository flightRepository,
                                CurrentUser currentUser) {
        this.bookingService = bookingService;
        this.flightRepository = flightRepository;
        this.currentUser = currentUser;
    }

    @GetMapping("/booking/flight/{flightId}")
    public String flightForm(@PathVariable Long flightId,
                             @RequestParam(required = false) String tripCity,
                             @RequestParam(required = false) String tripAirport,
                             Authentication auth, Model model, HttpSession session) {
        Flight f = flightRepository.findById(flightId).orElse(null);
        if (f == null) return "redirect:/flights";
        if (tripCity != null && !tripCity.isBlank()) {
            session.setAttribute("tripCity", tripCity);
            session.setAttribute("tripAirport", tripAirport);
        }
        model.addAttribute("flight", f);
        model.addAttribute("fullName", currentUser.require(auth).getFullName());
        return "booking/flight-form";
    }

    @PostMapping("/booking/flight")
    public String bookFlight(@RequestParam Long flightId,
                             @RequestParam String passengerName,
                             @RequestParam(required = false) String contactEmail,
                             @RequestParam(defaultValue = "1") int seats,
                             Authentication auth, RedirectAttributes ra) {
        try {
            Booking b = bookingService.createFlightBooking(currentUser.id(auth), flightId,
                    passengerName, contactEmail, seats);
            return "redirect:/payment/" + b.getPublicCode();
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/booking/flight/" + flightId;
        }
    }

    @PostMapping("/booking/hotel")
    public String bookHotel(@RequestParam Long hotelId,
                            @RequestParam Long roomTypeId,
                            @RequestParam(required = false) String roomName,
                            @RequestParam String guestName,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
                            @RequestParam(defaultValue = "1") int rooms,
                            Authentication auth, RedirectAttributes ra) {
        try {
            Booking b = bookingService.createHotelBooking(currentUser.id(auth), hotelId, roomTypeId,
                    roomName, guestName, checkIn, checkOut, rooms);
            return "redirect:/payment/" + b.getPublicCode();
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/hotels/" + hotelId;
        }
    }
}
