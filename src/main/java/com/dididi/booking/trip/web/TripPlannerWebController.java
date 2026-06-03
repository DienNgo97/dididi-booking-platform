package com.dididi.booking.trip.web;

import com.dididi.booking.trip.service.TripPlannerService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class TripPlannerWebController {

    private final TripPlannerService tripPlannerService;

    public TripPlannerWebController(TripPlannerService tripPlannerService) {
        this.tripPlannerService = tripPlannerService;
    }

    @GetMapping("/trip-planner")
    public String form() {
        return "trip/planner";
    }

    @GetMapping("/trip-planner/suggest")
    public String suggest(@RequestParam String city,
                          @RequestParam(required = false) String from,
                          Model model) {
        model.addAttribute("suggestion", tripPlannerService.suggest(city, from));
        return "trip/suggest";
    }
}
