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
import com.dididi.booking.review.domain.enums.ReviewImageKind;
import com.dididi.booking.review.service.ReviewImageService;
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
    private final ReviewImageService reviewImageService;
    private final HotelImageService hotelImageService;
    private final WishlistService wishlistService;
    private final CurrentUser currentUser;

    public HotelWebController(HotelRepository hotelRepository, PmsApiAdapter pmsAdapter,
                              RoomTypeRepository roomTypeRepository, ReviewService reviewService,
                              ReviewImageService reviewImageService,
                              HotelImageService hotelImageService, WishlistService wishlistService,
                              CurrentUser currentUser) {
        this.hotelRepository = hotelRepository;
        this.pmsAdapter = pmsAdapter;
        this.roomTypeRepository = roomTypeRepository;
        this.reviewService = reviewService;
        this.reviewImageService = reviewImageService;
        this.hotelImageService = hotelImageService;
        this.wishlistService = wishlistService;
        this.currentUser = currentUser;
    }

    @GetMapping("/hotels")
    public String list(@RequestParam(required = false) String city,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String checkIn,
                       @RequestParam(required = false) String checkOut,
                       @RequestParam(required = false) Integer adults,
                       @RequestParam(required = false) Integer children,
                       @RequestParam(required = false) Integer rooms,
                       @RequestParam(required = false) String stay,
                       @RequestParam(required = false) String trip,
                       Authentication auth, Model model) {
        // Tim kiem theo tu khoa: uu tien tham so 'keyword', neu khong co thi dung 'city' (cung o tim kiem chung).
        // Tu khoa khop ten / thanh pho / dia chi khach san, khong phan biet hoa thuong.
        String q = (keyword != null && !keyword.isBlank()) ? keyword.trim()
                 : (city != null && !city.isBlank()) ? city.trim() : null;
        List<Hotel> hotels = (q == null)
                ? hotelRepository.findByActiveTrue()
                : hotelRepository.searchActiveByKeyword(q);
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
        model.addAttribute("city", q);
        model.addAttribute("keyword", q);
        model.addAttribute("checkIn", checkIn);
        model.addAttribute("checkOut", checkOut);
        model.addAttribute("adults", adults);
        model.addAttribute("children", children);
        model.addAttribute("rooms", rooms);
        model.addAttribute("stay", stay);
        model.addAttribute("trip", trip);
        Long uid = currentUser.idOrNull(auth);
        if (uid != null) model.addAttribute("wishlistedIds", wishlistService.wishlistedHotelIds(uid));
        return "hotels/list";
    }

    @GetMapping("/hotels/{id}")
    public String detail(@PathVariable Long id,
                         @RequestParam(required = false) String tripCity,
                         @RequestParam(required = false) String tripAirport,
                         @RequestParam(required = false) String checkIn,
                         @RequestParam(required = false) String checkOut,
                         @RequestParam(required = false) Integer adults,
                         @RequestParam(required = false) Integer children,
                         @RequestParam(name = "rooms", required = false) Integer roomsWanted,
                         @RequestParam(required = false) String stay,
                         @RequestParam(required = false) String date,
                         @RequestParam(required = false) String timeIn,
                         @RequestParam(required = false) String timeOut,
                         @RequestParam(required = false) Integer keepRooms,
                         @RequestParam(required = false) String trip,
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
        // Phan bo so phong goi y theo suc chua tung hang phong:
        //   soPhong = ceil(soKhach / sucChua). VD: 4 khach -> phong suc chua 2 can 2 phong; suc chua 3 can 2 phong.
        int guests = (adults != null ? adults : 2) + (children != null ? children : 0);
        if (guests < 1) guests = 1;
        Map<Long, Integer> suggestRooms = new LinkedHashMap<>();
        for (RoomTypeItem r : rooms) {
            Integer cap = r.capacity();
            int q = (cap != null && cap > 0) ? ((guests + cap - 1) / cap) : 1;
            suggestRooms.put(r.id(), q < 1 ? 1 : q);
        }
        model.addAttribute("guests", guests);
        model.addAttribute("suggestRooms", suggestRooms);
        model.addAttribute("checkIn", checkIn);
        model.addAttribute("checkOut", checkOut);
        model.addAttribute("roomsWanted", roomsWanted);
        model.addAttribute("stay", stay);
        model.addAttribute("date", date);       // prefill ngay (cho o trong ngay) khi dat loi
        model.addAttribute("timeIn", timeIn);    // prefill gio nhan
        model.addAttribute("timeOut", timeOut);  // prefill gio tra
        model.addAttribute("trip", trip);         // co luong Trip Planner (giu qua form dat phong)
        model.addAttribute("keepRooms", keepRooms); // prefill so phong khi dat loi (khong de be goi y suc chua p7)

        // Danh gia khach san (chi PUBLISHED)
        Page<Review> rp = reviewService.list(BookingType.HOTEL, hotel.getId(), 0, 50);
        List<Review> reviews = rp.getContent();
        Map<Long, List<String>> reviewImages = new LinkedHashMap<>();
        Map<Long, List<String>> replyImages = new LinkedHashMap<>();
        for (Review rv : reviews) {
            reviewImages.put(rv.getId(), reviewImageService.listUrls(rv.getId(), ReviewImageKind.REVIEW));
            replyImages.put(rv.getId(), reviewImageService.listUrls(rv.getId(), ReviewImageKind.REPLY));
        }
        model.addAttribute("hotel", hotel);
        model.addAttribute("images", hotelImageService.listImages(hotel.getId()));
        model.addAttribute("rooms", rooms);
        model.addAttribute("avgRating", reviewService.averageRating(BookingType.HOTEL, hotel.getId()));
        model.addAttribute("reviewCount", rp.getTotalElements());
        model.addAttribute("reviews", reviews);
        model.addAttribute("reviewImages", reviewImages);
        model.addAttribute("replyImages", replyImages);
        Long uid = currentUser.idOrNull(auth);
        model.addAttribute("wishlisted", uid != null && wishlistService.isWishlisted(uid, hotel.getId()));
        return "hotels/detail";
    }
}
