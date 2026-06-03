package com.dididi.booking.trip.dto;

import com.dididi.booking.flight.api.dto.FlightApiDto;
import com.dididi.booking.hotel.api.dto.HotelApiDto;

import java.util.List;

public record TripSuggestionDto(
        String city,
        String destinationAirport,
        List<FlightApiDto> flights,
        List<HotelApiDto> hotels) {

    public static TripSuggestionDto from(TripSuggestion s) {
        return new TripSuggestionDto(
                s.city(),
                s.destinationAirport(),
                s.flights().stream().map(FlightApiDto::from).toList(),
                s.hotels().stream().map(HotelApiDto::from).toList());
    }
}
