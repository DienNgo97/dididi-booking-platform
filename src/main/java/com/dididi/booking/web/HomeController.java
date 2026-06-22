package com.dididi.booking.web;

import com.dididi.booking.booking.domain.enums.BookingType;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.hotel.service.HotelImageService;
import com.dididi.booking.review.service.ReviewService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class HomeController {

    private final HotelRepository hotelRepository;
    private final ReviewService reviewService;
    private final HotelImageService hotelImageService;

    public HomeController(HotelRepository hotelRepository, ReviewService reviewService,
                          HotelImageService hotelImageService) {
        this.hotelRepository = hotelRepository;
        this.reviewService = reviewService;
        this.hotelImageService = hotelImageService;
    }

    @GetMapping({"/", "/home"})
    public String home(Model model) {
        model.addAttribute("featured",
                hotelRepository.findAll(PageRequest.of(0, 4)).getContent());

        // Top 10 khách sạn theo điểm trung bình cộng (chỉ KS đang hoạt động & đã có đánh giá PUBLISHED).
        // averageRating đã làm tròn 1 chữ số; lọc avg > 0 để loại KS chưa có đánh giá, rồi sắp giảm dần.
        List<Hotel> active = hotelRepository.findByActiveTrue();
        Map<Long, Double> avg = new HashMap<>();
        for (Hotel h : active) {
            double a = reviewService.averageRating(BookingType.HOTEL, h.getId());
            if (a > 0) avg.put(h.getId(), a);
        }
        List<Hotel> topRated = active.stream()
                .filter(h -> avg.containsKey(h.getId()))
                .sorted(Comparator.comparingDouble((Hotel h) -> avg.get(h.getId())).reversed())
                .limit(10)
                .toList();
        Map<Long, String> topCovers = new LinkedHashMap<>();
        for (Hotel h : topRated) {
            topCovers.put(h.getId(), hotelImageService.firstImageUrl(h.getId()));
        }
        model.addAttribute("topRated", topRated);
        model.addAttribute("topRatings", avg);
        model.addAttribute("topCovers", topCovers);
        return "home";
    }
}
