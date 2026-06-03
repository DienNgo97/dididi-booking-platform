package com.dididi.booking.hotel.web;

import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.integration.dto.RoomTypeItem;
import com.dididi.booking.integration.service.PmsApiAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class HotelWebController {

    private static final Logger log = LoggerFactory.getLogger(HotelWebController.class);

    private final HotelRepository hotelRepository;
    private final PmsApiAdapter pmsAdapter;

    public HotelWebController(HotelRepository hotelRepository, PmsApiAdapter pmsAdapter) {
        this.hotelRepository = hotelRepository;
        this.pmsAdapter = pmsAdapter;
    }

    @GetMapping("/hotels")
    public String list(@RequestParam(required = false) String city, Model model) {
        List<Hotel> hotels = (city == null || city.isBlank())
                ? hotelRepository.findByActiveTrue()
                : hotelRepository.findByActiveTrueAndCityContainingIgnoreCase(city);
        model.addAttribute("hotels", hotels);
        model.addAttribute("city", city);
        return "hotels/list";
    }

    @GetMapping("/hotels/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Hotel hotel = hotelRepository.findById(id).orElse(null);
        if (hotel == null) {
            return "redirect:/hotels";
        }
        List<RoomTypeItem> rooms = List.of();
        if (hotel.getExternalId() != null) {
            try {
                rooms = pmsAdapter.fetchRooms(hotel.getExternalId());
            } catch (Exception ex) {
                log.warn("Cannot fetch rooms for hotel {}: {}", id, ex.toString());
            }
        }
        model.addAttribute("hotel", hotel);
        model.addAttribute("rooms", rooms);
        return "hotels/detail";
    }
}
