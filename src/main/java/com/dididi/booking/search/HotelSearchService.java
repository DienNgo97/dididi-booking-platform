package com.dididi.booking.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tìm kiếm khách sạn qua MEILISEARCH (full-text: gõ không dấu / sai chính tả vẫn ra kết quả).
 *
 * KIẾN TRÚC: MySQL vẫn là NGUỒN SỰ THẬT duy nhất — Meilisearch chỉ là INDEX ĐỌC, đồng bộ MỘT CHIỀU
 * từ MySQL bởi {@link HotelSearchIndexer}. Vì vậy không có bài toán consistency 2 chiều:
 * index hỏng/lệch thì re-index lại từ MySQL là xong.
 *
 * BẬT bằng cờ: app.search.enabled=true + chạy Meilisearch local (xem HUONG-DAN-MEILISEARCH.md).
 * Tắt cờ hoặc Meilisearch chết -> {@link #searchIds} trả null -> caller FALLBACK về MySQL LIKE như cũ
 * (không bao giờ làm gãy trang tìm kiếm).
 */
@Service
public class HotelSearchService {

    private static final Logger log = LoggerFactory.getLogger(HotelSearchService.class);
    static final String INDEX = "hotels";

    private final boolean enabled;
    private final RestClient client;

    public HotelSearchService(
            @Value("${app.search.enabled:false}") boolean enabled,
            @Value("${app.search.host:http://localhost:7700}") String host,
            @Value("${app.search.api-key:}") String apiKey) {
        this.enabled = enabled;
        RestClient.Builder b = RestClient.builder().baseUrl(host);
        if (apiKey != null && !apiKey.isBlank()) {
            b.defaultHeader("Authorization", "Bearer " + apiKey);
        }
        this.client = b.build();
    }

    public boolean isEnabled() { return enabled; }

    /**
     * Tìm theo từ khoá, trả về danh sách hotelId theo ĐỘ LIÊN QUAN (relevance) giảm dần.
     * Trả {@code null} khi search tắt hoặc Meilisearch lỗi -> caller tự fallback về MySQL.
     *
     * matchingStrategy:
     *   - Lượt 1 dùng "all" (mọi từ trong query đều phải khớp) — nếu không, mặc định của Meili là "last":
     *     "phuong ngoc ha" sẽ trả cả KS chỉ khớp mỗi chữ "phuong" -> đếm 351/358 KS, nhìn như bug.
     *   - "all" chặt quá có thể ra 0 (vd người dùng gõ kèm từ thừa: "khach san o da nang") -> lượt 2
     *     thử lại với "last" (nới lỏng dần từ cuối) để vẫn có kết quả gần đúng.
     */
    public List<Long> searchIds(String q, int limit) {
        if (!enabled || q == null || q.isBlank()) {
            return null;
        }
        try {
            List<Long> ids = doSearch(q.trim(), limit, "all");
            if (ids != null && ids.isEmpty() && q.trim().contains(" ")) {
                ids = doSearch(q.trim(), limit, "last");
            }
            return ids;
        } catch (Exception e) {
            // Meilisearch chưa chạy / lỗi mạng -> log nhẹ rồi để caller fallback MySQL.
            log.warn("[search] Meilisearch không phản hồi ({}) -> fallback MySQL.", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Long> doSearch(String q, int limit, String matchingStrategy) {
        Map<String, Object> body = client.post()
                .uri("/indexes/" + INDEX + "/search")
                .body(Map.of("q", q, "limit", limit,
                        "matchingStrategy", matchingStrategy,
                        "attributesToRetrieve", List.of("id")))
                .retrieve()
                .body(Map.class);
        if (body == null || !(body.get("hits") instanceof List<?> hits)) {
            return null;
        }
        List<Long> ids = new ArrayList<>(hits.size());
        for (Object h : hits) {
            Object id = ((Map<String, Object>) h).get("id");
            if (id instanceof Number n) ids.add(n.longValue());
        }
        return ids;
    }

    // ===== dùng bởi HotelSearchIndexer =====

    /** Đẩy (upsert) toàn bộ documents vào index. */
    void putDocuments(List<Map<String, Object>> docs) {
        client.post().uri("/indexes/" + INDEX + "/documents?primaryKey=id")
                .body(docs).retrieve().toBodilessEntity();
    }

    /** Xoá toàn bộ documents (trước khi re-index để loại KS đã tắt/xoá). */
    void clearDocuments() {
        client.delete().uri("/indexes/" + INDEX + "/documents").retrieve().toBodilessEntity();
    }

    /** P2: gỡ NGAY một khách sạn khỏi index khi bị tắt/xoá, không đợi đợt re-index 15 phút. */
    void deleteDocument(Long hotelId) {
        client.delete().uri("/indexes/" + INDEX + "/documents/" + hotelId).retrieve().toBodilessEntity();
    }

    /** Cài đặt index: field nào tìm được / lọc được / sắp xếp được. Idempotent. */
    void applySettings() {
        Map<String, Object> settings = Map.of(
                "searchableAttributes", List.of("name", "city", "ward", "province", "address", "description"),
                "filterableAttributes", List.of("city", "province", "starRating", "propertyType", "amenities", "tags", "minPrice"),
                "sortableAttributes", List.of("minPrice", "starRating"),
                // Ưu tiên khớp tên KS rồi mới tới địa chỉ (thứ tự searchableAttributes đã thể hiện điều này)
                "typoTolerance", Map.of("enabled", true)
        );
        client.patch().uri("/indexes/" + INDEX + "/settings")
                .body(settings).retrieve().toBodilessEntity();
    }
}
