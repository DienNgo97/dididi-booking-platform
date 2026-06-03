package com.dididi.booking.trip.api;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.trip.dto.SuggestRequest;
import com.dididi.booking.trip.dto.TripSuggestionDto;
import com.dididi.booking.trip.service.TripPlannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Trip Planner")
@RestController
@RequestMapping("/api/v1/trip-planner")
public class TripPlannerApiController {

    private final TripPlannerService tripPlannerService;

    public TripPlannerApiController(TripPlannerService tripPlannerService) {
        this.tripPlannerService = tripPlannerService;
    }

    @Operation(summary = "Gợi ý chuyến bay + khách sạn theo thành phố điểm đến")
    @PostMapping("/suggest")
    public ApiResponse<TripSuggestionDto> suggest(@RequestBody SuggestRequest req) {
        return ApiResponse.ok(TripSuggestionDto.from(
                tripPlannerService.suggest(req.city(), req.from())));
    }
}
