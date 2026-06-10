package com.dididi.booking.hotel.service;

import com.dididi.booking.hotel.api.dto.HotelApiDto;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.repository.HotelRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Doc khach san co cache (Redis). Tach khoi controller de @Cacheable chay qua Spring proxy
 * (goi tu controller la bean khac nen cache moi an). Cache het han theo TTL (app.cache.ttl-minutes).
 * Du lieu khach san it doi nen cache an toan; neu muon tuoi ngay sau khi sua/duyet khach san
 * thi them @CacheEvict(value = {"hotelsByCity","hotelById"}, allEntries = true) o cho ghi (xem README).
 */
@Service
public class HotelQueryService {

    private final HotelRepository hotelRepository;

    public HotelQueryService(HotelRepository hotelRepository) {
        this.hotelRepository = hotelRepository;
    }

    @Cacheable(value = "hotelsByCity", key = "#city == null ? '_all' : #city.toLowerCase()")
    public List<HotelApiDto> listActive(String city) {
        List<Hotel> all = (city == null || city.isBlank())
                ? hotelRepository.findByActiveTrue()
                : hotelRepository.findByActiveTrueAndCityContainingIgnoreCase(city);
        return all.stream().map(HotelApiDto::from).toList();
    }

    @Cacheable(value = "hotelById", key = "#id", unless = "#result == null")
    public HotelApiDto findById(Long id) {
        return hotelRepository.findById(id).map(HotelApiDto::from).orElse(null);
    }
}
