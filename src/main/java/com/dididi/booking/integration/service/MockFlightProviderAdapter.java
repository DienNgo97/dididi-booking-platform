package com.dididi.booking.integration.service;

import com.dididi.booking.integration.dto.FlightBookResult;
import com.dididi.booking.integration.dto.FlightItem;
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
}
