package com.dididi.booking.hotel.service;

import com.dididi.booking.booking.domain.enums.BookingType;
import com.dididi.booking.hotel.api.dto.HotelApiDto;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.review.service.ReviewService;
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
    private final ReviewService reviewService;
    private final com.dididi.booking.search.HotelSearchService hotelSearchService;

    public HotelQueryService(HotelRepository hotelRepository, ReviewService reviewService,
                             com.dididi.booking.search.HotelSearchService hotelSearchService) {
        this.hotelRepository = hotelRepository;
        this.reviewService = reviewService;
        this.hotelSearchService = hotelSearchService;
    }

    @Cacheable(value = "hotelsByCityV2", key = "#city == null ? '_all' : #city.toLowerCase()")
    public List<HotelApiDto> listActive(String city) {
        List<Hotel> all = (city == null || city.isBlank())
                ? hotelRepository.findByActiveTrue()
                : hotelRepository.findByActiveTrueAndCityContainingIgnoreCase(city);
        // Kèm điểm đánh giá trung bình để mobile lọc/sắp xếp (0.0 nếu chưa có review).
        // FIX N+1 (cùng họ M5): rating lấy theo LÔ 1 query GROUP BY thay vì 1 query/KS —
        // trước đây được Redis che (cache miss đầu tiên vẫn ~1.800 query với data x5).
        java.util.Map<Long, Double> rmap = reviewService.averageRatings(
                BookingType.HOTEL, all.stream().map(Hotel::getId).toList());
        return all.stream()
                .map(h -> HotelApiDto.from(h, rmap.getOrDefault(h.getId(), 0.0)))
                .toList();
    }

    /**
     * Tìm khách sạn theo TỪ KHOÁ cho API mobile — cùng cơ chế với web /hotels/data:
     * Meilisearch trước (gõ không dấu / sai chính tả vẫn ra, xếp theo độ liên quan),
     * Meili tắt hoặc lỗi thì fallback MySQL LIKE. KHÔNG cache (kết quả theo từ khoá tự do,
     * cache sẽ phình key; Meili đã đủ nhanh).
     */
    public List<HotelApiDto> searchActive(String q) {
        if (q == null || q.isBlank()) return List.of();
        List<Long> ids = hotelSearchService.searchIds(q.trim(), 500);
        List<Hotel> base;
        if (ids != null) {
            java.util.Map<Long, Hotel> byId = new java.util.LinkedHashMap<>();
            for (Hotel h : hotelRepository.findAllById(ids)) {
                if (h.isActive()) byId.put(h.getId(), h);
            }
            base = new java.util.ArrayList<>(ids.size());
            for (Long id : ids) {                      // giữ đúng thứ tự relevance của Meili
                Hotel h = byId.get(id);
                if (h != null) base.add(h);
            }
        } else {
            base = hotelRepository.searchActiveByKeyword(q.trim());
        }
        java.util.Map<Long, Double> rmap = reviewService.averageRatings(
                BookingType.HOTEL, base.stream().map(Hotel::getId).toList());
        return base.stream()
                .map(h -> HotelApiDto.from(h, rmap.getOrDefault(h.getId(), 0.0)))
                .toList();
    }

    @Cacheable(value = "hotelByIdV2", key = "#id", unless = "#result == null")
    public HotelApiDto findById(Long id) {
        // Endpoint public: khách sạn inactive coi như không tồn tại (không lộ qua API chi tiết).
        return hotelRepository.findById(id)
                .filter(Hotel::isActive)
                .map(HotelApiDto::from)
                .orElse(null);
    }
}
