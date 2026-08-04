package com.dididi.booking.trip.web;

import com.dididi.booking.trip.dto.TripGuideAnswer;
import com.dididi.booking.trip.service.TripGuideService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Locale;

/**
 * AI HƯỚNG DẪN VIÊN DU LỊCH — trang riêng /trip-guide (tách khỏi Trip Planner, có mục nav
 * + banner trang chủ vì là tính năng điểm nhấn của đồ án).
 *
 *  - GET  /trip-guide      : trang chat toàn màn. Nhận ?q=... để tự hỏi ngay câu đầu
 *                            (các chip trên trang chủ link tới đây kèm sẵn câu hỏi).
 *  - POST /trip-guide/ask  : hỏi 1 lượt (JSON) — hybrid KB + LLM (TripGuideService).
 *                            permitAll + CSRF-exempt (xem SecurityWebConfig, giống /support/ask).
 *
 * API cho mobile vẫn ở TripPlannerApiController: POST /api/v1/trip-planner/guide.
 */
@Controller
public class TripGuideWebController {

    private final TripGuideService tripGuideService;

    public TripGuideWebController(TripGuideService tripGuideService) {
        this.tripGuideService = tripGuideService;
    }

    @GetMapping("/trip-guide")
    public String page(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("presetQuestion", q == null ? "" : q);
        return "trip/guide";
    }

    @PostMapping("/trip-guide/ask")
    @ResponseBody
    public TripGuideAnswer ask(@RequestParam("q") String q, Locale locale) {
        return tripGuideService.answer(q, locale);
    }
}
