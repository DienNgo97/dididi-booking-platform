package com.dididi.booking.trip.service;

import com.dididi.booking.flight.domain.entity.Flight;
import com.dididi.booking.flight.repository.FlightRepository;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.trip.dto.TripSuggestion;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Trip Planner ban RUT GON: tu thanh pho diem den -> goi y chuyen bay (theo san bay) + khach san.
 * Khong dung Google Maps (bo phan goi y dia diem tham quan).
 */
@Service
public class TripPlannerService {

    // thanh pho (lowercase) -> ma san bay
    private static final Map<String, String> CITY_TO_AIRPORT = new LinkedHashMap<>();
    static {
        CITY_TO_AIRPORT.put("tp.hcm", "SGN");
        CITY_TO_AIRPORT.put("ho chi minh", "SGN");
        CITY_TO_AIRPORT.put("sai gon", "SGN");
        CITY_TO_AIRPORT.put("ha noi", "HAN");
        CITY_TO_AIRPORT.put("da nang", "DAD");
        CITY_TO_AIRPORT.put("hue", "HUI");
        CITY_TO_AIRPORT.put("nha trang", "CXR");
        CITY_TO_AIRPORT.put("phu quoc", "PQC");
    }

    private final FlightRepository flightRepository;
    private final HotelRepository hotelRepository;

    public TripPlannerService(FlightRepository flightRepository, HotelRepository hotelRepository) {
        this.flightRepository = flightRepository;
        this.hotelRepository = hotelRepository;
    }

    public TripSuggestion suggest(String city, String fromAirport) {
        String norm = normalize(city);
        String destAirport = resolveAirport(norm);

        List<Hotel> hotels = (city == null || city.isBlank())
                ? List.of()
                : hotelRepository.findByActiveTrueAndCityContainingIgnoreCase(city);

        List<Flight> flights = (destAirport == null)
                ? List.of()
                : flightRepository.findAllByOrderByDepartureTime().stream()
                .filter(f -> destAirport.equalsIgnoreCase(f.getToAirport()))
                .filter(f -> fromAirport == null || fromAirport.isBlank()
                        || fromAirport.equalsIgnoreCase(f.getFromAirport()))
                .toList();

        return new TripSuggestion(city, destAirport, flights, hotels);
    }

    /** Ma san bay cua thanh pho diem den (cong khai cho luong trip-planner). */
    public String airportFor(String city) {
        return resolveAirport(normalize(city));
    }

    /** Chuyen bay CON TRONG (con ghe) tu 'from' -> 'to' dung NGAY 'date'. */
    public List<Flight> availableFlights(String from, String to, LocalDate date) {
        if (from == null || from.isBlank() || to == null || to.isBlank() || date == null) return List.of();
        return flightRepository.findAllByOrderByDepartureTime().stream()
                .filter(f -> to.equalsIgnoreCase(f.getToAirport()))
                .filter(f -> from.equalsIgnoreCase(f.getFromAirport()))
                .filter(f -> f.getDepartureTime() != null && date.equals(f.getDepartureTime().toLocalDate()))
                .filter(f -> f.getAvailableSeats() == null || f.getAvailableSeats() > 0)
                .toList();
    }

    private String resolveAirport(String normalizedCity) {
        if (normalizedCity.isBlank()) return null;
        for (Map.Entry<String, String> e : CITY_TO_AIRPORT.entrySet()) {
            if (normalizedCity.contains(e.getKey()) || e.getKey().contains(normalizedCity)) {
                return e.getValue();
            }
        }
        return null;
    }

    /** Chuan hoa: bo dau tieng Viet + lowercase de so khop ten thanh pho (vd "Đà Nẵng" -> "da nang"). */
    private static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.replace('đ', 'd').replace('Đ', 'D').toLowerCase();
    }
}
