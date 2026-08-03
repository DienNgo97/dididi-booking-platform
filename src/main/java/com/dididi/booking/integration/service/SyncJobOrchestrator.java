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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
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
    private final CacheManager cacheManager;
    // Tu inject proxy chinh minh (BP-INT-01): goi syncFlights()/syncHotels() qua proxy de @Transactional co hieu luc.
    private final ObjectProvider<SyncJobOrchestrator> selfProvider;

    public SyncJobOrchestrator(FlightDataSource flightDataSource,
                               HotelInventorySource hotelInventorySource,
                               SyncLogRepository syncLogRepository,
                               ExternalDataSourceRepository sourceRepository,
                               FlightRepository flightRepository,
                               HotelRepository hotelRepository,
                               CacheManager cacheManager,
                               ObjectProvider<SyncJobOrchestrator> selfProvider) {
        this.flightDataSource = flightDataSource;
        this.hotelInventorySource = hotelInventorySource;
        this.syncLogRepository = syncLogRepository;
        this.sourceRepository = sourceRepository;
        this.flightRepository = flightRepository;
        this.hotelRepository = hotelRepository;
        this.cacheManager = cacheManager;
        this.selfProvider = selfProvider;
    }

    public void syncAll() {
        // Goi qua proxy (self) de @Transactional tren syncFlights/syncHotels duoc weave (BP-INT-01).
        SyncJobOrchestrator self = selfProvider.getObject();
        int flights = runSource("FLIGHT_PROVIDER", self::syncFlights);
        int hotels = runSource("HOTEL_PMS", self::syncHotels);
        log.info("Inventory sync finished: synced {} flights, {} hotels", flights, hotels);
    }

    @Transactional
    public int syncFlights() {
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
        // Sync co the doi gia/lich chuyen bay -> bo cache catalog ve (BP-CACHE-01).
        evictCaches("flightSearch", "flightById");
        return items.size();
    }

    @Transactional
    public int syncHotels() {
        List<HotelItem> items = hotelInventorySource.fetchHotels();
        for (HotelItem it : items) {
            Hotel h = hotelRepository.findByExternalId(it.id()).orElseGet(Hotel::new);
            boolean isNew = (h.getId() == null);
            h.setExternalId(it.id());
            h.setName(it.name());
            h.setCity(it.city());
            // Địa chỉ: KHÔNG đè chuỗi thô của PMS lên KS đã có địa chỉ tách chuẩn hoá
            // (backfill format VN mới: số nhà, đường, phường/xã, tỉnh — bỏ quận/huyện).
            // PMS trả format cũ nên nếu đè sẽ hoàn tác công chuẩn hoá sau mỗi 15 phút.
            boolean hasStructuredAddress = (h.getWard() != null && !h.getWard().isBlank())
                    || (h.getProvince() != null && !h.getProvince().isBlank());
            if (hasStructuredAddress) {
                h.setAddress(com.dididi.booking.hotel.domain.HotelSupport.composeAddress(
                        h.getHouseNumber(), h.getStreet(), h.getWard(), h.getDistrict(),
                        h.getProvince(), h.getCity()));
            } else {
                h.setAddress(it.address());
            }
            h.setDescription(it.description());
            h.setStarRating(it.starRating());
            // BP-SYNC-02: chi bat active khi lan dau them khach san. Voi khach san da ton tai,
            // GIU NGUYEN co active do admin dat (khong hoi sinh khach san admin da tat).
            if (isNew) {
                h.setActive(true);
            }
            hotelRepository.save(h);
        }
        // Sync co the doi thong tin khach san -> bo cache hotel (BP-CACHE-01).
        evictCaches("hotelsByCityV2", "hotelByIdV2");
        return items.size();
    }

    /** Xoa toan bo entry cua cac cache theo ten (best-effort; cache co the chua dang ky). */
    private void evictCaches(String... names) {
        if (cacheManager == null) return;
        for (String name : names) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        }
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
