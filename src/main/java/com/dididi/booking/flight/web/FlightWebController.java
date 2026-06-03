package com.dididi.booking.flight.web;

import com.dididi.booking.flight.domain.entity.Flight;
import com.dididi.booking.flight.repository.FlightRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
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
                       Model model) {
        List<Flight> flights = flightRepository.findAllByOrderByDepartureTime().stream()
                .filter(f -> from == null || from.isBlank() || from.equalsIgnoreCase(f.getFromAirport()))
                .filter(f -> to == null || to.isBlank() || to.equalsIgnoreCase(f.getToAirport()))
                .filter(f -> date == null || (f.getDepartureTime() != null && f.getDepartureTime().toLocalDate().equals(date)))
                .toList();
        model.addAttribute("flights", flights);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("date", date);
        return "flights/list";
    }
}
