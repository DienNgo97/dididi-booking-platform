package com.dididi.booking.integration.service;

import com.dididi.booking.flight.domain.entity.Flight;
import com.dididi.booking.flight.repository.FlightRepository;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.integration.domain.entity.SyncLog;
import com.dididi.booking.integration.domain.enums.SyncStatus;
import com.dididi.booking.integration.dto.FlightItem;
import com.dididi.booking.integration.dto.HotelItem;
import com.dididi.booking.integration.repository.ExternalDataSourceRepository;
import com.dididi.booking.integration.repository.SyncLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/**
 * Dieu phoi 1 lan sync: goi adapter -> luu data xuong DB local -> ghi sync_logs.
 * Loi tung source duoc bat rieng nen app van song.
 */
@Service
public class SyncJobOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SyncJobOrchestrator.class);

    private final FlightDataSource flightDataSource;
    private final HotelInventorySource hotelInventorySource;
    private final SyncLogRepository syncLogRepository;
    private final ExternalDataSourceRepository sourceRepository;
    private final FlightRepository flightRepository;
    private final HotelRepository hotelRepository;

    public SyncJobOrchestrator(FlightDataSource flightDataSource,
                               HotelInventorySource hotelInventorySource,
                               SyncLogRepository syncLogRepository,
                               ExternalDataSourceRepository sourceRepository,
                               FlightRepository flightRepository,
                               HotelRepository hotelRepository) {
        this.flightDataSource = flightDataSource;
        this.hotelInventorySource = hotelInventorySource;
        this.syncLogRepository = syncLogRepository;
        this.sourceRepository = sourceRepository;
        this.flightRepository = flightRepository;
        this.hotelRepository = hotelRepository;
    }

    public void syncAll() {
        int flights = runSource("FLIGHT_PROVIDER", this::syncFlights);
        int hotels = runSource("HOTEL_PMS", this::syncHotels);
        log.info("Inventory sync finished: synced {} flights, {} hotels", flights, hotels);
    }

    @Transactional
    protected int syncFlights() {
        List<FlightItem> items = flightDataSource.fetchFlights();
        for (FlightItem it : items) {
            Flight f = flightRepository.findByExternalId(it.id()).orElseGet(Flight::new);
            f.setExternalId(it.id());
            f.setFlightNumber(it.flightNumber());
            f.setAirlineCode(it.airlineCode());
            f.setFromAirport(it.from());
            f.setToAirport(it.to());
            f.setDepartureTime(it.departureTime());
            f.setArrivalTime(it.arrivalTime());
            f.setPrice(it.price());
            if (it.currency() != null) f.setCurrency(it.currency());
            f.setAvailableSeats(it.availableSeats());
            f.setAircraftType(it.aircraftType());
            flightRepository.save(f);
        }
        return items.size();
    }

    @Transactional
    protected int syncHotels() {
        List<HotelItem> items = hotelInventorySource.fetchHotels();
        for (HotelItem it : items) {
            Hotel h = hotelRepository.findByExternalId(it.id()).orElseGet(Hotel::new);
            h.setExternalId(it.id());
            h.setName(it.name());
            h.setCity(it.city());
            h.setAddress(it.address());
            h.setDescription(it.description());
            h.setStarRating(it.starRating());
            h.setActive(true);
            hotelRepository.save(h);
        }
        return items.size();
    }

    private int runSource(String code, Supplier<Integer> task) {
        Instant start = Instant.now();
        SyncLog logEntry = new SyncLog();
        logEntry.setSourceCode(code);
        logEntry.setStartedAt(start);
        try {
            int count = task.get();
            logEntry.setStatus(SyncStatus.SUCCESS);
            logEntry.setItemsSynced(count);
            logEntry.setMessage("OK");
            sourceRepository.findByCode(code).ifPresent(s -> {
                s.setLastSyncAt(Instant.now());
                sourceRepository.save(s);
            });
            return count;
        } catch (Exception ex) {
            log.warn("Sync FAILED for source {}: {}", code, ex.toString());
            logEntry.setStatus(SyncStatus.FAILED);
            logEntry.setItemsSynced(0);
            logEntry.setMessage(ex.toString().length() > 480 ? ex.toString().substring(0, 480) : ex.toString());
            return 0;
        } finally {
            logEntry.setFinishedAt(Instant.now());
            syncLogRepository.save(logEntry);
        }
    }
}
