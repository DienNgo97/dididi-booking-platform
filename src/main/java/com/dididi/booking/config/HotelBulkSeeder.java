package com.dididi.booking.config;

import com.dididi.booking.hotel.domain.HotelSupport;
import com.dididi.booking.hotel.domain.VnLocations;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.domain.entity.RoomType;
import com.dididi.booking.hotel.domain.enums.Amenity;
import com.dididi.booking.hotel.domain.enums.HotelSource;
import com.dididi.booking.hotel.domain.enums.HotelTag;
import com.dididi.booking.hotel.domain.enums.PropertyType;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.hotel.repository.RoomTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

/**
 * Seed 300 KHÁCH SẠN MỚI với địa chỉ đúng format Việt Nam sau sáp nhập hành chính 1/7/2025:
 *   số nhà, đường, phường/xã, tỉnh/TP (KHÔNG còn quận/huyện).
 *
 * BẬT bằng cờ: app.seed.hotels300=true  (mặc định false để không chạy mỗi lần khởi động).
 *   - VM/env:  -Dapp.seed.hotels300=true   hoặc   APP_SEED_HOTELS300=true
 *   - hoặc thêm vào application-local.yml:  app: { seed: { hotels300: true } }
 *
 * IDEMPOTENT: đánh dấu bằng dải externalId 800001..800300; nếu đã seed thì bỏ qua (chạy lại an toàn).
 * Chạy trong 1 transaction (all-or-nothing). Mỗi KS có 3-4 hạng phòng, còn trống mặc định tới khi
 * có dòng inventory (giống cơ chế DemoDataSeeder). Toạ độ jitter quanh trung tâm để hiện trên Google Maps.
 *
 * Cùng cờ này, {@link HotelAddressBackfill} sẽ CHUẨN HOÁ địa chỉ các khách sạn CŨ về đúng format trên.
 */
@Component
@Profile("dev")
@Order(110)
public class HotelBulkSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(HotelBulkSeeder.class);

    /** Dải externalId dành riêng cho KS seed hàng loạt (đánh dấu + idempotent). */
    public static final long EXT_BASE = 800_000L;

    @Value("${app.seed.hotels300:false}")
    private boolean enabled;

    /** Phase 2 (18/08/2026): +72 KS cho 24 tỉnh/thành còn lại -> phủ đủ 34 đơn vị cấp tỉnh sau sáp nhập. */
    public static final long EXT2_BASE = 805_000L;

    @Value("${app.seed.hotels34:false}")
    private boolean enabled34;

    private final HotelRepository hotelRepository;
    private final RoomTypeRepository roomTypeRepository;

    private final Random rnd = new Random(20260627L); // tất định để chạy lại cho kết quả ổn định

    private static final String[] PRE = {"Saigon", "Hanoi", "Bay", "Riverside", "Sunrise", "Golden",
            "Royal", "Ocean", "Central", "Grand", "Lotus", "Pearl", "Emerald", "Hoa Sen", "Bình Minh",
            "Mường Thanh", "An Phú", "Đông Dương", "Sao Mai", "Hải Âu"};
    private static final String[] SUF = {"Hotel", "Resort", "Boutique Hotel", "Inn", "Suites",
            "Hotel & Spa", "Beach Resort", "Residence", "Homestay", "Villa"};

    // mẫu hạng phòng: tên, sức chứa, giá gốc (VND), tổng số phòng, diện tích (m2)
    private static final Object[][] RTPL = {
        {"Standard", 2, 600000, 20, 22},
        {"Superior", 2, 850000, 16, 26},
        {"Deluxe",   3, 1200000, 12, 32},
        {"Family",   4, 1600000, 8, 40},
        {"Suite",    4, 2400000, 6, 55},
    };

    public HotelBulkSeeder(HotelRepository hotelRepository, RoomTypeRepository roomTypeRepository) {
        this.hotelRepository = hotelRepository;
        this.roomTypeRepository = roomTypeRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (enabled) seed300();
        if (enabled34) seed34();
    }

    private void seed300() {
        if (hotelRepository.findByExternalId(EXT_BASE + 1).isPresent()) {
            // Đã seed trước đó -> KHÔNG tạo lại. Nhưng tự sửa các KS seed cũ bị đánh nhầm source=CHANNEL
            // (khiến detail() đi tìm phòng ở hotel-pms 8082, không có -> không đặt được) về DIRECT.
            int fixed = 0;
            for (Hotel h : hotelRepository.findByActiveTrue()) {
                if (h.getExternalId() != null && h.getExternalId() >= EXT_BASE && h.getSource() != HotelSource.DIRECT) {
                    h.setSource(HotelSource.DIRECT);
                    hotelRepository.save(h);
                    fixed++;
                }
            }
            log.info("[HotelBulkSeeder] Đã seed 300 KS trước đó -> bỏ qua tạo mới. Sửa {} KS sang DIRECT để đặt phòng được.", fixed);
            return;
        }
        log.info("[HotelBulkSeeder] Bắt đầu seed 300 khách sạn (địa chỉ chuẩn VN sau sáp nhập 2025)...");

        Set<String> usedNames = new HashSet<>();
        int seq = 0;
        for (VnLocations.Loc loc : VnLocations.ALL) {
            for (int n = 0; n < loc.weight; n++) {
                seq++;
                int star = 3 + rnd.nextInt(3);                 // 3..5
                double mult = priceMult(star, loc);
                Hotel h = buildHotel(loc, seq, star, mult, usedNames);
                hotelRepository.save(h);
                seedRoomTypes(h, mult);
            }
        }
        log.info("[HotelBulkSeeder] HOÀN TẤT: đã tạo {} khách sạn mới trên {} thành phố. Tổng KS hiện có = {}.",
                seq, VnLocations.ALL.size(), hotelRepository.count());
    }

    /**
     * Phase 2: 24 tỉnh/thành còn lại (VnLocations.EXTRA, 3 KS/điểm = 72 KS) -> đủ 34 đơn vị cấp tỉnh.
     * Idempotent bằng dải externalId 805001..805072, độc lập với phase 300 (chạy được riêng lẻ).
     */
    private void seed34() {
        if (hotelRepository.findByExternalId(EXT2_BASE + 1).isPresent()) {
            log.info("[HotelBulkSeeder] Phase 34 tỉnh: đã seed trước đó -> bỏ qua.");
            return;
        }
        log.info("[HotelBulkSeeder] Phase 34 tỉnh: seed {} điểm đến x3 KS...", VnLocations.EXTRA.size());
        Set<String> usedNames = new HashSet<>();
        int seq = 0;
        for (VnLocations.Loc loc : VnLocations.EXTRA) {
            for (int n = 0; n < loc.weight; n++) {
                seq++;
                int star = 3 + rnd.nextInt(3);
                double mult = priceMult(star, loc);
                Hotel h = buildHotel(loc, seq, star, mult, usedNames);
                h.setExternalId(EXT2_BASE + seq);   // ghi đè dải id phase 2 (buildHotel gán dải phase 1)
                hotelRepository.save(h);
                seedRoomTypes(h, mult);
            }
        }
        log.info("[HotelBulkSeeder] Phase 34 tỉnh HOÀN TẤT: +{} KS. Tổng KS = {}.", seq, hotelRepository.count());
    }

    // ----- build -----

    private Hotel buildHotel(VnLocations.Loc loc, int seq, int star, double mult, Set<String> usedNames) {
        String suffix = SUF[rnd.nextInt(SUF.length)];
        String house = String.valueOf(1 + rnd.nextInt(400));
        String street = loc.streets[rnd.nextInt(loc.streets.length)];
        String ward = loc.wards[rnd.nextInt(loc.wards.length)];

        Hotel h = new Hotel();
        h.setExternalId(EXT_BASE + seq);
        h.setName(uniqueName(loc.city, suffix, usedNames));
        h.setCity(loc.city);
        // ----- địa chỉ tách (format VN mới: KHÔNG quận/huyện) -----
        h.setHouseNumber(house);
        h.setStreet(street);
        h.setWard(ward);
        h.setDistrict(null);
        h.setProvince(loc.province);
        h.setAddress(HotelSupport.composeAddress(house, street, ward, null, loc.province, loc.city));
        // ----- toạ độ quanh trung tâm (±~2.5km) cho Google Maps -----
        h.setLat(round6(loc.lat + (rnd.nextDouble() - 0.5) * 0.045));
        h.setLng(round6(loc.lng + (rnd.nextDouble() - 0.5) * 0.045));
        h.setRegion(loc.region);
        // ----- thông tin hiển thị -----
        h.setStarRating(star);
        h.setDescription(descFor(loc, star));
        h.setActive(true);
        // DIRECT: loại phòng được seed thẳng vào DB nội bộ (giống DemoDataSeeder) nên trang chi tiết
        // đọc phòng từ roomTypeRepository và ĐẶT PHÒNG ĐƯỢC. (CHANNEL sẽ đi tìm phòng ở hotel-pms 8082
        // -> không có dữ liệu 800xxx -> "Chưa lấy được loại phòng" -> không đặt được.)
        h.setSource(HotelSource.DIRECT);
        h.setCurrency("VND");
        h.setPropertyType(propertyTypeOf(suffix));
        Set<Amenity> ams = randomAmenities(star);
        h.setAmenities(ams);
        h.setTags(tagsFor(loc, star, ams));
        long minPrice = round10k(((Number) RTPL[0][2]).longValue() * mult); // giá hạng rẻ nhất (Standard)
        h.setMinPrice(BigDecimal.valueOf(minPrice));
        return h;
    }

    private void seedRoomTypes(Hotel h, double mult) {
        int numTypes = 3 + rnd.nextInt(2); // 3 hoặc 4 hạng phòng
        for (int t = 0; t < numTypes; t++) {
            Object[] tpl = RTPL[t];
            long price = round10k(((Number) tpl[2]).longValue() * mult);
            RoomType rt = new RoomType();
            rt.setHotelId(h.getId());
            rt.setName((String) tpl[0]);
            rt.setCapacity((int) tpl[1]);
            rt.setBasePrice(BigDecimal.valueOf(price));
            rt.setCurrency("VND");
            rt.setTotalRooms((int) tpl[3]);
            rt.setAreaSqm((int) tpl[4]);
            roomTypeRepository.save(rt);
        }
    }

    // ----- helpers -----

    private String uniqueName(String city, String suffix, Set<String> used) {
        String nameCity = "TP.HCM".equals(city) ? "Sài Gòn" : city;
        for (int tries = 0; tries < 60; tries++) {
            String nm = PRE[rnd.nextInt(PRE.length)] + " " + suffix + " " + nameCity;
            if (used.add(nm)) return nm;
        }
        String nm = PRE[rnd.nextInt(PRE.length)] + " " + suffix + " " + nameCity + " " + (used.size() + 1);
        used.add(nm);
        return nm;
    }

    private static String descFor(VnLocations.Loc loc, int star) {
        return "Khách sạn " + star + " sao tại " + loc.city + ", " + loc.province
                + ". Vị trí thuận tiện, gần các điểm tham quan và ẩm thực địa phương.";
    }

    /** Hệ số giá theo hạng sao + nhóm thành phố (resort biển/cao nguyên đắt hơn). */
    private double priceMult(int star, VnLocations.Loc loc) {
        double base = 0.85 + rnd.nextDouble() * 0.55;       // 0.85..1.40
        double starF = 1.0 + (star - 3) * 0.28;             // 3 sao=1.0, 5 sao≈1.56
        return base * cityTier(loc.city) * starF;
    }

    private static double cityTier(String city) {
        switch (city) {
            case "Phú Quốc": case "Đà Nẵng": case "Nha Trang": return 1.30;
            case "Hạ Long": case "Hội An": case "Vũng Tàu": return 1.20;
            case "TP.HCM": case "Hà Nội": return 1.18;
            case "Đà Lạt": case "Sa Pa": return 1.10;
            default: return 1.0; // Huế, Cần Thơ
        }
    }

    private static long round10k(double v) {
        return Math.max(1, Math.round(v / 10000.0)) * 10000L;
    }

    private static double round6(double v) {
        return Math.round(v * 1_000_000d) / 1_000_000d;
    }

    private static PropertyType propertyTypeOf(String suffix) {
        String s = suffix.toLowerCase();
        if (s.contains("resort")) return PropertyType.RESORT;
        if (s.contains("villa")) return PropertyType.VILLA;
        if (s.contains("homestay")) return PropertyType.HOMESTAY;
        if (s.contains("residence") || s.contains("suites")) return PropertyType.APARTMENT;
        if (s.contains("inn")) return PropertyType.GUESTHOUSE;
        return PropertyType.HOTEL;
    }

    private Set<Amenity> randomAmenities(int star) {
        Set<Amenity> s = new LinkedHashSet<>();
        s.add(Amenity.WIFI); s.add(Amenity.AC); s.add(Amenity.RECEPTION_24H); s.add(Amenity.PARKING);
        Amenity[] pool = Amenity.values();
        int extra = 4 + rnd.nextInt(6);
        for (int i = 0; i < extra; i++) s.add(pool[rnd.nextInt(pool.length)]);
        if (star >= 4) { s.add(Amenity.BREAKFAST); s.add(Amenity.POOL); }
        if (star >= 5) { s.add(Amenity.SPA); s.add(Amenity.GYM); s.add(Amenity.RESTAURANT); }
        return s;
    }

    private Set<HotelTag> tagsFor(VnLocations.Loc loc, int star, Set<Amenity> ams) {
        Set<HotelTag> t = new LinkedHashSet<>();
        boolean beach = isBeach(loc.city);
        if (beach) { t.add(HotelTag.SEA_VIEW); if (rnd.nextBoolean()) t.add(HotelTag.BEACHFRONT); }
        if (loc.city.equals("TP.HCM") || loc.city.equals("Hà Nội")) t.add(HotelTag.CITY_CENTER);
        if (loc.city.equals("Đà Lạt") || loc.city.equals("Sa Pa")) { t.add(HotelTag.QUIET); t.add(HotelTag.ROMANTIC); }
        if (star >= 5) t.add(HotelTag.LUXURY); else if (star == 3) t.add(HotelTag.BUDGET);
        if (ams.contains(Amenity.FAMILY_ROOM) || rnd.nextInt(3) == 0) t.add(HotelTag.FAMILY_FRIENDLY);
        if (ams.contains(Amenity.AIRPORT_SHUTTLE)) t.add(HotelTag.NEAR_AIRPORT);
        if (ams.contains(Amenity.SPA)) t.add(HotelTag.ROMANTIC);
        return t;
    }

    private static boolean isBeach(String city) {
        switch (city) {
            case "Nha Trang": case "Đà Nẵng": case "Phú Quốc":
            case "Vũng Tàu": case "Hạ Long": case "Hội An":
                return true;
            default:
                return false;
        }
    }
}
