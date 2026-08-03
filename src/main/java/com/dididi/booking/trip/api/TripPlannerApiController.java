package com.dididi.booking.trip.api;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.trip.dto.SuggestRequest;
import com.dididi.booking.trip.dto.TripGuideAnswer;
import com.dididi.booking.trip.dto.TripSuggestionDto;
import com.dididi.booking.trip.service.TripGuideService;
import com.dididi.booking.trip.service.TripPlannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;

@Tag(name = "Trip Planner")
@RestController
@RequestMapping("/api/v1/trip-planner")
public class TripPlannerApiController {

    private final TripPlannerService tripPlannerService;
    private final TripGuideService tripGuideService;

    public TripPlannerApiController(TripPlannerService tripPlannerService, TripGuideService tripGuideService) {
        this.tripPlannerService = tripPlannerService;
        this.tripGuideService = tripGuideService;
    }

    @Operation(summary = "Gợi ý chuyến bay + khách sạn theo thành phố điểm đến")
    @PostMapping("/suggest")
    public ApiResponse<TripSuggestionDto> suggest(@RequestBody SuggestRequest req) {
        return ApiResponse.ok(TripSuggestionDto.from(
                tripPlannerService.suggest(req.city(), req.from())));
    }

    /**
     * AI hướng dẫn viên du lịch cho app mobile (KB + LLM tuỳ chọn — hybrid như chatbot CSKH).
     * Body: {"q": "lịch trình 3 ngày ở Đà Nẵng"}. permitAll qua rule POST /api/v1/trip-planner/**
     * có sẵn trong SecurityApiConfig (khách chưa đăng nhập vẫn hỏi được — đồng bộ guest browsing).
     */
    @Operation(summary = "AI hướng dẫn viên du lịch: lịch trình theo giờ, đi lại, ăn uống, vui chơi")
    @PostMapping("/guide")
    public ApiResponse<TripGuideAnswer> guide(@RequestBody Map<String, String> body, Locale locale) {
        return ApiResponse.ok(tripGuideService.answer(body.getOrDefault("q", ""), locale));
    }
}
