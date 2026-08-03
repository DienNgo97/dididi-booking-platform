package com.dididi.booking.search;

import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.repository.HotelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Đồng bộ MỘT CHIỀU MySQL -> Meilisearch cho index "hotels".
 *
 * Chiến lược đơn giản & đủ cho quy mô hiện tại (~360 KS): re-index TOÀN BỘ khi app khởi động
 * và định kỳ 15 phút (cùng nhịp sync PMS — nên dữ liệu tên/địa chỉ mới từ PMS cũng vào index).
 * KS inactive KHÔNG được index (không lộ qua tìm kiếm). Nếu Meilisearch chưa chạy -> log warn,
 * KHÔNG làm app lỗi; lần chạy định kỳ sau sẽ tự index lại khi Meilisearch sống.
 */
@Component
public class HotelSearchIndexer {

    private static final Logger log = LoggerFactory.getLogger(HotelSearchIndexer.class);

    private final HotelSearchService searchService;
    private final HotelRepository hotelRepository;

    public HotelSearchIndexer(HotelSearchService searchService, HotelRepository hotelRepository) {
        this.searchService = searchService;
        this.hotelRepository = hotelRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        reindex();
    }

    /** Re-index định kỳ (15 phút) — bắt kịp thay đổi từ admin/vendor/sync PMS. */
    @Scheduled(fixedDelay = 15 * 60 * 1000L, initialDelay = 15 * 60 * 1000L)
    public void scheduled() {
        reindex();
    }

    public void reindex() {
        if (!searchService.isEnabled()) {
            return;
        }
        try {
            List<Hotel> hotels = hotelRepository.findByActiveTrue();
            List<Map<String, Object>> docs = new ArrayList<>(hotels.size());
            for (Hotel h : hotels) {
                docs.add(toDoc(h));
            }
            searchService.applySettings();
            searchService.clearDocuments();          // loại KS đã tắt/xoá khỏi index
            searchService.putDocuments(docs);
            log.info("[search] Đã re-index {} khách sạn vào Meilisearch.", docs.size());
        } catch (Exception e) {
            log.warn("[search] Không re-index được (Meilisearch chưa chạy?): {}", e.getMessage());
        }
    }

    private static Map<String, Object> toDoc(Hotel h) {
        Map<String, Object> d = new HashMap<>();
        d.put("id", h.getId());
        d.put("name", h.getName());
        d.put("city", h.getCity());
        d.put("ward", h.getWard());
        d.put("province", h.getProvince());
        d.put("address", h.getAddress());
        d.put("description", h.getDescription());
        d.put("starRating", h.getStarRating());
        d.put("minPrice", h.getMinPrice() != null ? h.getMinPrice().longValue() : null);
        d.put("propertyType", h.getPropertyType() != null ? h.getPropertyType().name() : null);
        d.put("amenities", h.getAmenities().stream().map(Enum::name).toList());
        d.put("tags", h.getTags().stream().map(Enum::name).toList());
        if (h.hasGeo()) {
            d.put("_geo", Map.of("lat", h.getLat(), "lng", h.getLng()));
        }
        return d;
    }
}
