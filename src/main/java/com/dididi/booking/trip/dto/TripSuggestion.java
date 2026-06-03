package com.dididi.booking.trip.dto;

import com.dididi.booking.flight.domain.entity.Flight;
import com.dididi.booking.hotel.domain.entity.Hotel;

import java.util.List;

public record TripSuggestion(
        String city,
        String destinationAirport,
        List<Flight> flights,
        List<Hotel> hotels) {
}
