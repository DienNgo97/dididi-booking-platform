package com.dididi.booking.hotel.web;

import com.dididi.booking.booking.domain.enums.BookingType;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.domain.enums.HotelSource;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.hotel.repository.RoomTypeRepository;
import com.dididi.booking.hotel.service.HotelImageService;
import com.dididi.booking.integration.dto.RoomTypeItem;
import com.dididi.booking.integration.service.PmsApiAdapter;
import com.dididi.booking.review.domain.entity.Review;
import com.dididi.booking.review.service.ReviewService;
import com.dididi.booking.web.CurrentUser;
import com.dididi.booking.wishlist.service.WishlistService;
import org.springframework.security.core.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class HotelWebController {

    private static final Logger log = LoggerFactory.getLogger(HotelWebController.class);

    private final HotelRepository hotelRepository;
    private final PmsApiAdapter pmsAdapter;
    private final RoomTypeRepository roomTypeRepository;
    private final ReviewService reviewService;
    private final HotelImageService hotelImageService;
    private final WishlistService wishlistService;
    private final CurrentUser currentUser;

    public HotelWebController(HotelRepository hotelRepository, PmsApiAdapter pmsAdapter,
                              RoomTypeRepository roomTypeRepository, ReviewService reviewService,
                              HotelImageService hotelImageService, WishlistService wishlistService,
                              CurrentUser currentUser) {
        this.hotelRepository = hotelRepository;
        this.pmsAdapter = pmsAdapter;
        this.roomTypeRepository = roomTypeRepository;
        this.reviewService = reviewService;
        this.hotelImageService = hotelImageService;
        this.wishlistService = wishlistService;
        this.currentUser = currentUser;
    }

    @GetMapping("/hotels")
    public String list(@RequestParam(required = false) String city, Authentication auth, Model model) {
        List<Hotel> hotels = (city == null || city.isBlank())
                ? hotelRepository.findByActiveTrue()
                : hotelRepository.findByActiveTrueAndCityContainingIgnoreCase(city);
        // Diem trung binh moi khach san (0.0 = chua co danh gia)
        Map<Long, Double> ratings = new LinkedHashMap<>();
        Map<Long, String> covers = new LinkedHashMap<>();
        for (Hotel h : hotels) {
            ratings.put(h.getId(), reviewService.averageRating(BookingType.HOTEL, h.getId()));
            covers.put(h.getId(), hotelImageService.firstImageUrl(h.getId()));
        }
        model.addAttribute("hotels", hotels);
        model.addAttribute("ratings", ratings);
        model.addAttribute("covers", covers);
        model.addAttribute("city", city);
        Long uid = currentUser.idOrNull(auth);
        if (uid != null) model.addAttribute("wishlistedIds", wishlistService.wishlistedHotelIds(uid));
        return "hotels/list";
    }

    @GetMapping("/hotels/{id}")
    public String detail(@PathVariable Long id,
                         @RequestParam(required = false) String tripCity,
                         @RequestParam(required = false) String tripAirport,
                         Authentication auth, Model model, HttpSession session) {
        Hotel hotel = hotelRepository.findById(id).orElse(null);
        if (hotel == null) {
            return "redirect:/hotels";
        }
        if (tripCity != null && !tripCity.isBlank()) {
            session.setAttribute("tripCity", tripCity);
            session.setAttribute("tripAirport", tripAirport);
        }
        List<RoomTypeItem> rooms = List.of();
        if (hotel.getSource() == HotelSource.DIRECT) {
            // Khach san vendor tu quan: lay loai phong tu DB noi bo, map sang RoomTypeItem
            // de tai dung dung template + form dat phong.
            rooms = roomTypeRepository.findByHotelIdOrderByBasePrice(hotel.getId()).stream()
                    .map(rt -> new RoomTypeItem(rt.getId(), rt.getHotelId(), rt.getName(), rt.getDescription(),
                            rt.getCapacity(), rt.getBasePrice(), rt.getCurrency(), rt.getTotalRooms()))
                    .toList();
        } else if (hotel.getExternalId() != null) {
            try {
                rooms = pmsAdapter.fetchRooms(hotel.getExternalId());
            } catch (Exception ex) {
                log.warn("Cannot fetch rooms for hotel {}: {}", id, ex.toString());
            }
        }
        // Danh gia khach san (chi PUBLISHED)
        Page<Review> rp = reviewService.list(BookingType.HOTEL, hotel.getId(), 0, 50);
        model.addAttribute("hotel", hotel);
        model.addAttribute("images", hotelImageService.listImages(hotel.getId()));
        model.addAttribute("rooms", rooms);
        model.addAttribute("avgRating", reviewService.averageRating(BookingType.HOTEL, hotel.getId()));
        model.addAttribute("reviewCount", rp.getTotalElements());
        model.addAttribute("reviews", rp.getContent());
        Long uid = currentUser.idOrNull(auth);
        model.addAttribute("wishlisted", uid != null && wishlistService.isWishlisted(uid, hotel.getId()));
        return "hotels/detail";
    }
}
