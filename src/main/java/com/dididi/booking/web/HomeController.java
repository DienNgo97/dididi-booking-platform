package com.dididi.booking.web;

import com.dididi.booking.hotel.repository.HotelRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final HotelRepository hotelRepository;

    public HomeController(HotelRepository hotelRepository) {
        this.hotelRepository = hotelRepository;
    }

    @GetMapping({"/", "/home"})
    public String home(Model model) {
        model.addAttribute("featured",
                hotelRepository.findAll(PageRequest.of(0, 4)).getContent());
        return "home";
    }
}
