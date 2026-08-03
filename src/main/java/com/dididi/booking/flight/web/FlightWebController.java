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

    /** Số chuyến mỗi trang (phân trang PHÍA SERVER — với ~4.500 chuyến không thể render hết 1 lần). */
    private static final int PAGE_SIZE = 20;

    @GetMapping("/flights")
    public String list(@RequestParam(required = false) String from,
                       @RequestParam(required = false) String to,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                       @RequestParam(required = false) String cabin,
                       @RequestParam(defaultValue = "1") int page,
                       Model model) {
        // Chỉ hiện chuyến khởi hành SAU thời điểm hiện tại + 5 tiếng (đệm thời gian ra sân bay + boarding).
        LocalDateTime cutoff = LocalDateTime.now().plusHours(5);
        List<Flight> all = flightRepository.findAllByOrderByDepartureTime().stream()
                .filter(f -> f.getDepartureTime() != null && f.getDepartureTime().isAfter(cutoff))
                .filter(f -> from == null || from.isBlank() || from.equalsIgnoreCase(f.getFromAirport()))
                .filter(f -> to == null || to.isBlank() || to.equalsIgnoreCase(f.getToAirport()))
                .filter(f -> date == null || f.getDepartureTime().toLocalDate().equals(date))
                .toList();

        // PHÂN TRANG SERVER: chỉ render PAGE_SIZE chuyến/trang (trước đây render toàn bộ ~4.500 thẻ,
        // HTML nhiều MB + DOM nặng — cùng bài học tối ưu như /hotels, xem PERFORMANCE.md).
        int totalPages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int cur = Math.min(Math.max(1, page), totalPages);
        List<Flight> flights = all.subList((cur - 1) * PAGE_SIZE, Math.min(cur * PAGE_SIZE, all.size()));

        // Dãy số trang kiểu 1 … 4 5 6 … 99 (-1 = dấu "…") để template render đơn giản.
        List<Integer> pageItems = new java.util.ArrayList<>();
        if (totalPages <= 7) {
            for (int i = 1; i <= totalPages; i++) pageItems.add(i);
        } else {
            pageItems.add(1);
            int s = Math.max(2, cur - 1), e = Math.min(totalPages - 1, cur + 1);
            if (s > 2) pageItems.add(-1);
            for (int i = s; i <= e; i++) pageItems.add(i);
            if (e < totalPages - 1) pageItems.add(-1);
            pageItems.add(totalPages);
        }

        model.addAttribute("flights", flights);
        model.addAttribute("total", all.size());
        model.addAttribute("curPage", cur);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageItems", pageItems);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("date", date);
        model.addAttribute("cabin", cabin);
        return "flights/list";
    }
}
