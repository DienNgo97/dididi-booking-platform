package com.dididi.booking.hotel.web;

import com.dididi.booking.booking.domain.enums.BookingType;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.domain.enums.HotelSource;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.hotel.repository.RoomTypeRepository;
import com.dididi.booking.hotel.service.HotelImageService;
import com.dididi.booking.integration.dto.RoomTypeItem;
import com.dididi.booking.integration.service.PmsApiAdapter;
import com.dididi.booking.review.domain.entity.Review;
import com.dididi.booking.review.domain.enums.ReviewImageKind;
import com.dididi.booking.review.service.ReviewImageService;
import com.dididi.booking.review.service.ReviewService;
import com.dididi.booking.web.CurrentUser;
import com.dididi.booking.wishlist.service.WishlistService;
import org.springframework.security.core.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class HotelWebController {

    private static final Logger log = LoggerFactory.getLogger(HotelWebController.class);

    private final HotelRepository hotelRepository;
    private final PmsApiAdapter pmsAdapter;
    private final RoomTypeRepository roomTypeRepository;
    private final com.dididi.booking.hotel.repository.RoomInventoryRepository roomInventoryRepository;
    private final ReviewService reviewService;
    private final ReviewImageService reviewImageService;
    private final HotelImageService hotelImageService;
    private final WishlistService wishlistService;
    private final CurrentUser currentUser;
    private final com.dididi.booking.search.HotelSearchService hotelSearchService;

    public HotelWebController(HotelRepository hotelRepository, PmsApiAdapter pmsAdapter,
                              RoomTypeRepository roomTypeRepository, ReviewService reviewService,
                              ReviewImageService reviewImageService,
                              HotelImageService hotelImageService, WishlistService wishlistService,
                              CurrentUser currentUser,
                              com.dididi.booking.search.HotelSearchService hotelSearchService,
                              com.dididi.booking.hotel.repository.RoomInventoryRepository roomInventoryRepository) {
        this.roomInventoryRepository = roomInventoryRepository;
        this.hotelRepository = hotelRepository;
        this.pmsAdapter = pmsAdapter;
        this.roomTypeRepository = roomTypeRepository;
        this.reviewService = reviewService;
        this.reviewImageService = reviewImageService;
        this.hotelImageService = hotelImageService;
        this.wishlistService = wishlistService;
        this.currentUser = currentUser;
        this.hotelSearchService = hotelSearchService;
    }

    @GetMapping("/hotels")
    public String list(@RequestParam(required = false) String city,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String checkIn,
                       @RequestParam(required = false) String checkOut,
                       @RequestParam(required = false) Integer adults,
                       @RequestParam(required = false) Integer children,
                       @RequestParam(required = false) Integer rooms,
                       @RequestParam(required = false) String stay,
                       @RequestParam(required = false) String trip,
                       // ---- Bộ lọc kiểu Agoda (Nhóm A) ----
                       @RequestParam(required = false) Long priceMin,
                       @RequestParam(required = false) Long priceMax,
                       @RequestParam(required = false) List<Integer> stars,
                       @RequestParam(required = false) List<String> types,
                       @RequestParam(required = false) List<String> amenities,
                       @RequestParam(required = false) List<String> tags,
                       @RequestParam(required = false) Double minRating,
                       @RequestParam(required = false) String sort,
                       Authentication auth, Model model) {
        // Tim kiem theo tu khoa: uu tien tham so 'keyword', neu khong co thi dung 'city' (cung o tim kiem chung).
        String q = (keyword != null && !keyword.isBlank()) ? keyword.trim()
                 : (city != null && !city.isBlank()) ? city.trim() : null;

        // TOI UU (PF - AJAX paging): trang HTML chi con "khung" nhe (sidebar loc + o tim kiem).
        // Danh sach KS do client fetch tu GET /hotels/data (JSON phan trang server) — xem hotels/list.html.
        // Truoc day render 1.790 the o server ~3,6s/request; gio HTML tra ve gan nhu tuc thi.
        String s = sort == null ? "" : sort;
        model.addAttribute("city", q);
        model.addAttribute("keyword", q);
        model.addAttribute("checkIn", checkIn);
        model.addAttribute("checkOut", checkOut);
        model.addAttribute("adults", adults);
        model.addAttribute("children", children);
        model.addAttribute("rooms", rooms);
        model.addAttribute("stay", stay);
        model.addAttribute("trip", trip);
        // Trang thai bo loc (giu form) + danh muc
        model.addAttribute("fPriceMin", priceMin);
        model.addAttribute("fPriceMax", priceMax);
        model.addAttribute("fStars", stars == null ? List.of() : stars);
        model.addAttribute("fTypes", types == null ? List.of() : types);
        model.addAttribute("fAmenities", amenities == null ? List.of() : amenities);
        model.addAttribute("fTags", tags == null ? List.of() : tags);
        model.addAttribute("fMinRating", minRating);
        model.addAttribute("fSort", s);
        model.addAttribute("allAmenities", com.dididi.booking.hotel.domain.enums.Amenity.values());
        model.addAttribute("allTags", com.dididi.booking.hotel.domain.enums.HotelTag.values());
        model.addAttribute("allTypes", com.dididi.booking.hotel.domain.enums.PropertyType.values());
        return "hotels/list";
    }

    /**
     * DANH SÁCH KHÁCH SẠN dạng JSON — lọc + sắp xếp + PHÂN TRANG PHÍA SERVER (tối ưu hiệu năng).
     * Client (hotels/list.html) fetch endpoint này mỗi khi đổi bộ lọc/sắp xếp/trang (debounce ~300ms):
     * chỉ ~20 KS/lần thay vì render toàn bộ ~1.800 thẻ. Đếm tổng vẫn đúng trên TOÀN BỘ dữ liệu.
     * geo=true -> kèm toạ độ các KS khớp bộ lọc (tối đa 800) cho bản đồ Leaflet.
     * Ảnh bìa chỉ tải cho ĐÚNG trang hiện tại; rating batch 1 query (tận dụng fix M5).
     */
    @GetMapping("/hotels/data")
    @org.springframework.web.bind.annotation.ResponseBody
    public Map<String, Object> data(@RequestParam(required = false) String city,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) Long priceMin,
                                    @RequestParam(required = false) Long priceMax,
                                    @RequestParam(required = false) List<Integer> stars,
                                    @RequestParam(required = false) List<String> types,
                                    @RequestParam(required = false) List<String> amenities,
                                    @RequestParam(required = false) List<String> tags,
                                    @RequestParam(required = false) Double minRating,
                                    @RequestParam(required = false) String sort,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(defaultValue = "false") boolean geo,
                                    Authentication auth) {
        page = Math.max(1, page);
        size = Math.min(Math.max(size, 1), 50);
        String q = (keyword != null && !keyword.isBlank()) ? keyword.trim()
                 : (city != null && !city.isBlank()) ? city.trim() : null;
        boolean needFacets = (amenities != null && !amenities.isEmpty()) || (tags != null && !tags.isEmpty());

        // ----- 1) Nguồn: Meili (relevance) -> fallback LIKE; cần lọc tiện nghi thì fetch-join 1 query -----
        List<Hotel> base;
        if (q == null) {
            base = needFacets ? hotelRepository.findActiveTrueFetchFacets() : hotelRepository.findByActiveTrue();
        } else {
            List<Long> ids = hotelSearchService.searchIds(q, 500);
            List<Hotel> pool = needFacets ? hotelRepository.findActiveTrueFetchFacets() : null;
            if (ids != null) {
                Map<Long, Hotel> byId = new LinkedHashMap<>();
                List<Hotel> src = (pool != null) ? pool : hotelRepository.findAllById(ids);
                for (Hotel h : src) {
                    if (h.isActive()) byId.put(h.getId(), h);
                }
                base = new java.util.ArrayList<>(ids.size());
                for (Long id : ids) {
                    Hotel h = byId.get(id);
                    if (h != null) base.add(h);           // giữ đúng thứ tự relevance
                }
            } else {
                base = hotelRepository.searchActiveByKeyword(q);
                if (pool != null) {
                    java.util.Set<Long> keep = new java.util.HashSet<>();
                    for (Hotel h : base) keep.add(h.getId());
                    List<Hotel> withFacets = new java.util.ArrayList<>();
                    for (Hotel h : pool) if (keep.contains(h.getId())) withFacets.add(h);
                    base = withFacets;
                }
            }
        }

        // ----- 2) Lọc thuộc tính đơn giản (giá / sao / loại) -----
        java.util.Set<Integer> starSet = stars == null ? java.util.Set.of() : new java.util.HashSet<>(stars);
        java.util.Set<String> typeSet = types == null ? java.util.Set.of() : new java.util.HashSet<>(types);
        List<Hotel> filtered = new java.util.ArrayList<>(base.size());
        for (Hotel h : base) {
            Long price = h.getMinPrice() != null ? h.getMinPrice().longValue() : null;
            if (priceMin != null && (price == null || price < priceMin)) continue;
            if (priceMax != null && (price == null || price > priceMax)) continue;
            if (!starSet.isEmpty() && (h.getStarRating() == null || !starSet.contains(h.getStarRating()))) continue;
            if (!typeSet.isEmpty() && (h.getPropertyType() == null || !typeSet.contains(h.getPropertyType().name()))) continue;
            if (needFacets) {
                if (amenities != null && !amenities.isEmpty()) {   // tiện nghi: AND
                    java.util.Set<String> have = h.getAmenities().stream().map(Enum::name).collect(Collectors.toSet());
                    if (!have.containsAll(amenities)) continue;
                }
                if (tags != null && !tags.isEmpty()) {             // nổi bật: OR
                    java.util.Set<String> have = h.getTags().stream().map(Enum::name).collect(Collectors.toSet());
                    boolean any = false;
                    for (String t : tags) if (have.contains(t)) { any = true; break; }
                    if (!any) continue;
                }
            }
            filtered.add(h);
        }

        // ----- 3) Rating batch (1 query) -> lọc ngưỡng điểm -----
        List<Long> fids = filtered.stream().map(Hotel::getId).toList();
        Map<Long, Double> rmap = reviewService.averageRatings(BookingType.HOTEL, fids);
        if (minRating != null && minRating > 0) {
            filtered.removeIf(h -> rmap.getOrDefault(h.getId(), 0.0) < minRating);
        }

        // ----- 4) Sắp xếp -----
        String s = sort == null ? "" : sort;
        switch (s) {
            case "price_asc" -> filtered.sort(java.util.Comparator.comparingLong(
                    h -> h.getMinPrice() != null ? h.getMinPrice().longValue() : Long.MAX_VALUE));
            case "price_desc" -> filtered.sort(java.util.Comparator.comparingLong(
                    (Hotel h) -> h.getMinPrice() != null ? h.getMinPrice().longValue() : -1L).reversed());
            case "rating_desc" -> filtered.sort(java.util.Comparator.comparingDouble(
                    (Hotel h) -> rmap.getOrDefault(h.getId(), 0.0)).reversed());
            case "stars_desc" -> filtered.sort(java.util.Comparator.comparingInt(
                    (Hotel h) -> h.getStarRating() != null ? h.getStarRating() : 0).reversed());
            default -> { /* Đề xuất: giữ thứ tự nguồn (relevance Meili / thứ tự DB) */ }
        }

        // ----- 5) Phân trang + ảnh bìa CHỈ cho trang hiện tại + wishlist -----
        int total = filtered.size();
        int from = Math.min((page - 1) * size, total);
        int to = Math.min(from + size, total);
        List<Hotel> slice = filtered.subList(from, to);
        Map<Long, String> covers = hotelImageService.firstImageUrls(slice.stream().map(Hotel::getId).toList());
        Long uid = currentUser.idOrNull(auth);
        java.util.Set<Long> wish = uid == null ? java.util.Set.of()
                : new java.util.HashSet<>(wishlistService.wishlistedHotelIds(uid));

        List<Map<String, Object>> items = new java.util.ArrayList<>(slice.size());
        for (Hotel h : slice) {
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("id", h.getId());
            it.put("name", h.getName());
            it.put("city", h.getCity());
            it.put("address", h.getAddress());
            it.put("star", h.getStarRating());
            it.put("rating", rmap.getOrDefault(h.getId(), 0.0));
            it.put("price", h.getMinPrice() != null ? h.getMinPrice().longValue() : null);
            it.put("cover", covers.get(h.getId()));
            it.put("wishlisted", wish.contains(h.getId()));
            items.add(it);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("page", page);
        out.put("size", size);
        out.put("items", items);
        if (geo) {
            List<Map<String, Object>> pts = new java.util.ArrayList<>();
            for (Hotel h : filtered) {
                if (pts.size() >= 800) break;
                if (h.getLat() == null || h.getLng() == null) continue;
                Map<String, Object> g = new LinkedHashMap<>();
                g.put("id", h.getId());
                g.put("name", h.getName());
                g.put("lat", h.getLat());
                g.put("lng", h.getLng());
                g.put("price", h.getMinPrice() != null ? h.getMinPrice().longValue() : null);
                g.put("star", h.getStarRating());
                pts.add(g);
            }
            out.put("geo", pts);
        }
        return out;
    }

    @GetMapping("/hotels/{id}")
    public String detail(@PathVariable Long id,
                         @RequestParam(required = false) String tripCity,
                         @RequestParam(required = false) String tripAirport,
                         @RequestParam(required = false) String checkIn,
                         @RequestParam(required = false) String checkOut,
                         @RequestParam(required = false) Integer adults,
                         @RequestParam(required = false) Integer children,
                         @RequestParam(name = "rooms", required = false) Integer roomsWanted,
                         @RequestParam(required = false) String stay,
                         @RequestParam(required = false) String date,
                         @RequestParam(required = false) String timeIn,
                         @RequestParam(required = false) String timeOut,
                         @RequestParam(required = false) Integer keepRooms,
                         @RequestParam(required = false) String trip,
                         Authentication auth, Model model, HttpSession session) {
        Hotel hotel = hotelRepository.findById(id).orElse(null);
        if (hotel == null || !hotel.isActive()) {
            // KS không tồn tại hoặc đã bị admin tắt -> không cho xem chi tiết qua URL trực tiếp.
            return "redirect:/hotels";
        }
        if (tripCity != null && !tripCity.isBlank()) {
            session.setAttribute("tripCity", tripCity);
            session.setAttribute("tripAirport", tripAirport);
        }
        List<RoomTypeItem> rooms = List.of();
        if (hotel.getSource() == HotelSource.DIRECT) {
            // Khach san vendor tu quan: lay loai phong tu DB noi bo, map sang RoomTypeItem
            // de tai dung dung template + form dat phong.
            // TC-C-04: giá/đêm HIỂN THỊ phải khớp giá sẽ TÍNH TIỀN. Vendor đặt giá riêng theo ngày
            // (RoomInventory.price) — trước đây trang này in basePrice tĩnh nên khách chọn đúng ngày
            // đổi giá vẫn thấy giá cũ, tới bước thanh toán mới lộ giá mới. Giờ tính trung bình/đêm
            // cho đúng khoảng ngày khách chọn, cùng công thức inv.price ?? basePrice của BookingService.
            final String ciEff, coEff;
            if ("day".equals(stay) && date != null && !date.isBlank()) {
                String co2;
                try { co2 = java.time.LocalDate.parse(date).plusDays(1).toString(); } catch (Exception e) { co2 = null; }
                ciEff = date; coEff = co2;
            } else { ciEff = checkIn; coEff = checkOut; }
            rooms = roomTypeRepository.findByHotelIdOrderByBasePrice(hotel.getId()).stream()
                    .map(rt -> new RoomTypeItem(rt.getId(), rt.getHotelId(), rt.getName(), rt.getDescription(),
                            rt.getCapacity(), displayNightlyPrice(rt, ciEff, coEff), rt.getCurrency(), rt.getTotalRooms()))
                    .toList();
        } else if (hotel.getExternalId() != null) {
            try {
                rooms = pmsAdapter.fetchRooms(hotel.getExternalId());
            } catch (Exception ex) {
                log.warn("Cannot fetch rooms for hotel {}: {}", id, ex.toString());
            }
        }
        // Phan bo so phong goi y theo suc chua tung hang phong:
        //   soPhong = ceil(soKhach / sucChua). VD: 4 khach -> phong suc chua 2 can 2 phong; suc chua 3 can 2 phong.
        int guests = (adults != null ? adults : 2) + (children != null ? children : 0);
        if (guests < 1) guests = 1;
        Map<Long, Integer> suggestRooms = new LinkedHashMap<>();
        for (RoomTypeItem r : rooms) {
            Integer cap = r.capacity();
            int q = (cap != null && cap > 0) ? ((guests + cap - 1) / cap) : 1;
            suggestRooms.put(r.id(), q < 1 ? 1 : q);
        }
        model.addAttribute("guests", guests);
        model.addAttribute("suggestRooms", suggestRooms);
        model.addAttribute("checkIn", checkIn);
        model.addAttribute("checkOut", checkOut);
        model.addAttribute("roomsWanted", roomsWanted);
        model.addAttribute("stay", stay);
        model.addAttribute("date", date);       // prefill ngay (cho o trong ngay) khi dat loi
        model.addAttribute("timeIn", timeIn);    // prefill gio nhan
        model.addAttribute("timeOut", timeOut);  // prefill gio tra
        model.addAttribute("trip", trip);         // co luong Trip Planner (giu qua form dat phong)
        model.addAttribute("keepRooms", keepRooms); // prefill so phong khi dat loi (khong de be goi y suc chua p7)

        // Danh gia khach san (chi PUBLISHED)
        Page<Review> rp = reviewService.list(BookingType.HOTEL, hotel.getId(), 0, 50);
        List<Review> reviews = rp.getContent();
        Map<Long, List<String>> reviewImages = new LinkedHashMap<>();
        Map<Long, List<String>> replyImages = new LinkedHashMap<>();
        for (Review rv : reviews) {
            reviewImages.put(rv.getId(), reviewImageService.listUrls(rv.getId(), ReviewImageKind.REVIEW));
            replyImages.put(rv.getId(), reviewImageService.listUrls(rv.getId(), ReviewImageKind.REPLY));
        }
        model.addAttribute("hotel", hotel);
        model.addAttribute("images", hotelImageService.listImages(hotel.getId()));
        model.addAttribute("rooms", rooms);
        model.addAttribute("avgRating", reviewService.averageRating(BookingType.HOTEL, hotel.getId()));
        model.addAttribute("reviewCount", rp.getTotalElements());
        model.addAttribute("reviews", reviews);
        model.addAttribute("reviewImages", reviewImages);
        model.addAttribute("replyImages", replyImages);
        Long uid = currentUser.idOrNull(auth);
        model.addAttribute("wishlisted", uid != null && wishlistService.isWishlisted(uid, hotel.getId()));
        return "hotels/detail";
    }

    /** Giá/đêm hiệu dụng cho khoảng ngày đã chọn: trung bình các đêm theo RoomInventory override
     *  (cùng quy tắc tính tiền của BookingService). Thiếu ngày/parse lỗi -> basePrice như cũ. */
    private java.math.BigDecimal displayNightlyPrice(com.dididi.booking.hotel.domain.entity.RoomType rt,
                                                     String checkInStr, String checkOutStr) {
        try {
            if (checkInStr == null || checkInStr.isBlank() || checkOutStr == null || checkOutStr.isBlank()) {
                return rt.getBasePrice();
            }
            java.time.LocalDate ci = java.time.LocalDate.parse(checkInStr);
            java.time.LocalDate co = java.time.LocalDate.parse(checkOutStr);
            long nights = java.time.temporal.ChronoUnit.DAYS.between(ci, co);
            if (nights <= 0 || rt.getBasePrice() == null) return rt.getBasePrice();
            java.math.BigDecimal total = java.math.BigDecimal.ZERO;
            for (java.time.LocalDate d = ci; d.isBefore(co); d = d.plusDays(1)) {
                var inv = roomInventoryRepository.findByRoomTypeIdAndDate(rt.getId(), d).orElse(null);
                java.math.BigDecimal night = (inv != null && inv.getPrice() != null) ? inv.getPrice() : rt.getBasePrice();
                total = total.add(night);
            }
            return total.divide(java.math.BigDecimal.valueOf(nights), 0, java.math.RoundingMode.HALF_UP);
        } catch (Exception e) {
            return rt.getBasePrice();
        }
    }
}
