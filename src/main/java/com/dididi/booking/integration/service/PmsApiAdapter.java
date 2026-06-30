package com.dididi.booking.integration.service;

import com.dididi.booking.integration.dto.HotelItem;
import com.dididi.booking.integration.dto.ReserveResult;
import com.dididi.booking.integration.dto.RoomTypeItem;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Adapter goi hotel-pms (mock) qua REST, kem X-API-KEY.
 */
@Component
public class PmsApiAdapter implements HotelInventorySource {

    private static final Logger log = LoggerFactory.getLogger(PmsApiAdapter.class);

    private final RestClient client;

    public PmsApiAdapter(
            @Value("${app.integration.hotel-pms.endpoint}") String endpoint,
            @Value("${app.integration.hotel-pms.api-key}") String apiKey) {
        this.client = RestClient.builder()
                .baseUrl(endpoint)
                .defaultHeader("X-API-KEY", apiKey)
                .build();
    }

    @Override
    @Retry(name = "hotelPms")
    @CircuitBreaker(name = "hotelPms")
    public List<HotelItem> fetchHotels() {
        HotelItem[] arr = client.get().uri("/hotels").retrieve().body(HotelItem[].class);
        return arr == null ? List.of() : Arrays.asList(arr);
    }

    /** Loai phong cua 1 khach san (externalId ben hotel-pms). */
    @Retry(name = "hotelPms")
    @CircuitBreaker(name = "hotelPms")
    public List<RoomTypeItem> fetchRooms(Long hotelExternalId) {
        RoomTypeItem[] arr = client.get()
                .uri("/hotels/{id}/rooms", hotelExternalId)
                .retrieve().body(RoomTypeItem[].class);
        return arr == null ? List.of() : Arrays.asList(arr);
    }

    /**
     * Dat phong qua hotel-pms.
     * KHONG @Retry/@CircuitBreaker (BP-INT-03): /reserve khong idempotent — retry sau timeout/5xx
     * co the tao 2-3 reservation that + tru kho nhieu lan, de lai reservation mo coi. Loi de tang cho caller.
     */
    public ReserveResult reserve(Long hotelExternalId, Long roomTypeId, String guestName,
                                 LocalDate checkIn, LocalDate checkOut, int rooms) {
        log.debug("Reserving hotel {} roomType {} {}..{} x{}", hotelExternalId, roomTypeId, checkIn, checkOut, rooms);
        return client.post()
                .uri("/hotels/{id}/reserve", hotelExternalId)
                .body(Map.of("roomTypeId", roomTypeId,
                        "guestName", guestName,
                        "checkIn", checkIn.toString(),
                        "checkOut", checkOut.toString(),
                        "rooms", rooms))
                .retrieve()
                .body(ReserveResult.class);
    }

    /**
     * Huy 1 reservation ben hotel-pms theo reservationId (INT-01):
     * POST /reservations/{id}/cancel -> provider tra phong ve kho.
     * Dung khi huy/hoan tien don khach san CHANNEL (source != DIRECT).
     */
    public void cancel(Long reservationId) {
        client.post()
                .uri("/reservations/{id}/cancel", reservationId)
                .retrieve()
                .toBodilessEntity();
    }
}
