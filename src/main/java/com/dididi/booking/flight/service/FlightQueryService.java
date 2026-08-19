package com.dididi.booking.flight.service;

import com.dididi.booking.flight.api.dto.FlightApiDto;
import com.dididi.booking.flight.repository.FlightRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Tim/doc chuyen bay co cache (Redis). Key gom from|to|date. Cache het han theo TTL
 * (app.cache.ttl-minutes). Du lieu mock it doi nen cache an toan cho demo.
 */
@Service
public class FlightQueryService {

    private final FlightRepository flightRepository;

    public FlightQueryService(FlightRepository flightRepository) {
        this.flightRepository = flightRepository;
    }

    @Cacheable(value = "flightSearch",
            key = "(#from == null ? '' : #from.toLowerCase()) + '|' + (#to == null ? '' : #to.toLowerCase()) + '|' + (#date == null ? '' : #date.toString())")
    public List<FlightApiDto> search(String from, String to, LocalDate date) {
        // Chỉ chuyến provider (có sơ đồ ghế). Chuyến demo cục bộ (externalId >= 900000) bị loại.
        return flightRepository.findByExternalIdLessThanOrderByDepartureTime(
                        com.dididi.booking.booking.service.BookingService.LOCAL_FLIGHT_EXTERNAL_ID_BASE).stream()
                .filter(f -> from == null || from.isBlank() || from.equalsIgnoreCase(f.getFromAirport()))
                .filter(f -> to == null || to.isBlank() || to.equalsIgnoreCase(f.getToAirport()))
                .filter(f -> date == null
                        || (f.getDepartureTime() != null && f.getDepartureTime().toLocalDate().equals(date)))
                .map(FlightApiDto::from)
                .toList();
    }

    @Cacheable(value = "flightById", key = "#id", unless = "#result == null")
    public FlightApiDto findById(Long id) {
        return flightRepository.findById(id).map(FlightApiDto::from).orElse(null);
    }
}
