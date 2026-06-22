package com.dididi.booking.flight.web;

import com.dididi.booking.flight.domain.entity.Flight;
import com.dididi.booking.flight.repository.FlightRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Controller
public class FlightWebController {

    private final FlightRepository flightRepository;

    public FlightWebController(FlightRepository flightRepository) {
        this.flightRepository = flightRepository;
    }

    @GetMapping("/flights")
    public String list(@RequestParam(required = false) String from,
                       @RequestParam(required = false) String to,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                       @RequestParam(required = false) String cabin,
                       Model model) {
        // Chỉ hiện chuyến khởi hành SAU thời điểm hiện tại + 5 tiếng (đệm thời gian ra sân bay + boarding).
        LocalDateTime cutoff = LocalDateTime.now().plusHours(5);
        List<Flight> flights = flightRepository.findAllByOrderByDepartureTime().stream()
                .filter(f -> f.getDepartureTime() != null && f.getDepartureTime().isAfter(cutoff))
                .filter(f -> from == null || from.isBlank() || from.equalsIgnoreCase(f.getFromAirport()))
                .filter(f -> to == null || to.isBlank() || to.equalsIgnoreCase(f.getToAirport()))
                .filter(f -> date == null || f.getDepartureTime().toLocalDate().equals(date))
                .toList();
        model.addAttribute("flights", flights);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("date", date);
        model.addAttribute("cabin", cabin);
        return "flights/list";
    }
}
