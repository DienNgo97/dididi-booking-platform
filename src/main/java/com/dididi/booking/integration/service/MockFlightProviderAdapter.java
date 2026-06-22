package com.dididi.booking.integration.service;

import com.dididi.booking.integration.dto.FlightBookResult;
import com.dididi.booking.integration.dto.FlightItem;
import com.dididi.booking.integration.dto.SeatHoldResult;
import com.dididi.booking.integration.dto.SeatMapResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Adapter goi flight-provider (mock) qua REST, kem X-API-KEY.
 */
@Component
public class MockFlightProviderAdapter implements FlightDataSource {

    private static final Logger log = LoggerFactory.getLogger(MockFlightProviderAdapter.class);

    private final RestClient client;

    public MockFlightProviderAdapter(
            @Value("${app.integration.flight-provider.endpoint}") String endpoint,
            @Value("${app.integration.flight-provider.api-key}") String apiKey) {
        this.client = RestClient.builder()
                .baseUrl(endpoint)
                .defaultHeader("X-API-KEY", apiKey)
                .build();
    }

    @Override
    @Retry(name = "flightProvider")
    @CircuitBreaker(name = "flightProvider")
    public List<FlightItem> fetchFlights() {
        FlightItem[] arr = client.get().uri("/flights/search").retrieve().body(FlightItem[].class);
        return arr == null ? List.of() : Arrays.asList(arr);
    }

    /** Dat cho 1 chuyen bay (externalId la id ben flight-provider). */
    @Retry(name = "flightProvider")
    @CircuitBreaker(name = "flightProvider")
    public FlightBookResult bookFlight(Long externalId, String passengerName, String contactEmail, int seats) {
        log.debug("Booking flight {} for {} ({} seats)", externalId, passengerName, seats);
        return client.post()
                .uri("/flights/{id}/book", externalId)
                .body(Map.of("passengerName", passengerName,
                        "contactEmail", contactEmail == null ? "" : contactEmail,
                        "seats", seats))
                .retrieve()
                .body(FlightBookResult.class);
    }

    // ===== So do ghe / giu cho (seat selection) =====

    /** Lay so do ghe + gia + trang thai cua 1 chuyen. */
    @Retry(name = "flightProvider")
    @CircuitBreaker(name = "flightProvider")
    public SeatMapResult getSeatMap(Long externalId) {
        return client.get().uri("/flights/{id}/seats", externalId).retrieve().body(SeatMapResult.class);
    }

    /** Giu cho cac ghe (holdRef = ma don). Nem HttpClientErrorException neu 409 (ghe da co nguoi giu/dat). */
    public SeatHoldResult holdSeats(Long externalId, List<String> seatCodes, String holdRef, int minutes) {
        return client.post()
                .uri("/flights/{id}/seats/hold", externalId)
                .body(Map.of("seatCodes", seatCodes, "holdRef", holdRef, "holdMinutes", minutes))
                .retrieve()
                .body(SeatHoldResult.class);
    }

    /** Xac nhan ghe (HELD -> BOOKED) khi thanh toan thanh cong. */
    public void confirmSeats(Long externalId, String holdRef) {
        client.post()
                .uri("/flights/{id}/seats/confirm", externalId)
                .body(Map.of("holdRef", holdRef))
                .retrieve()
                .toBodilessEntity();
    }

    /** Nha cac ghe dang giu theo holdRef (huy/het han thanh toan). */
    public void releaseSeats(Long externalId, String holdRef) {
        client.post()
                .uri("/flights/{id}/seats/release", externalId)
                .body(Map.of("holdRef", holdRef))
                .retrieve()
                .toBodilessEntity();
    }
}
