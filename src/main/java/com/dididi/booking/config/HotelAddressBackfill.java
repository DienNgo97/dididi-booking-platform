package com.dididi.booking.config;

import com.dididi.booking.hotel.domain.HotelSupport;
import com.dididi.booking.hotel.domain.VnLocations;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.repository.HotelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * CHUẨN HOÁ địa chỉ các khách sạn CŨ về đúng format Việt Nam sau sáp nhập hành chính 1/7/2025:
 *   số nhà, đường, phường/xã, tỉnh/TP — KHÔNG còn quận/huyện.
 *
 * Sửa những điểm sai của địa chỉ cũ:
 *   - Bỏ cấp quận/huyện (đã bị bãi bỏ).
 *   - Đặt ĐÚNG tỉnh/TP sau sáp nhập (vd Vũng Tàu → TP. Hồ Chí Minh, Hội An → TP. Đà Nẵng,
 *     Nha Trang → Tỉnh Khánh Hòa...) thay vì đặt tỉnh = tên thành phố.
 *   - Thay phường giả ("Phường 5", "Quận 1"...) bằng phường/xã THẬT theo {@link VnLocations}.
 *   - Ghép lại chuỗi địa chỉ hiển thị theo đúng thứ tự.
 *
 * BẬT cùng cờ với seeder 300 KS: app.seed.hotels300=true.
 * IDEMPOTENT: chỉ đụng KS còn ở format cũ; chạy lại sẽ bỏ qua KS đã chuẩn (và KS seed mới externalId≥800000).
 */
@Component
@Profile("dev")
@Order(105)
public class HotelAddressBackfill implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(HotelAddressBackfill.class);

    @Value("${app.seed.hotels300:false}")
    private boolean enabled;

    private final HotelRepository hotelRepository;
    private final Random rnd = new Random(424242L);

    public HotelAddressBackfill(HotelRepository hotelRepository) {
        this.hotelRepository = hotelRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) return;

        List<Hotel> toSave = new ArrayList<>();
        for (Hotel h : hotelRepository.findByActiveTrue()) {
            if (!needsFix(h)) continue;

            VnLocations.Loc loc = VnLocations.byCity(h.getCity());
            // Bỏ cấp quận/huyện trong mọi trường hợp.
            h.setDistrict(null);

            String house = blank(h.getHouseNumber()) ? String.valueOf(1 + rnd.nextInt(300)) : h.getHouseNumber();
            String street = h.getStreet();
            String ward = h.getWard();
            String province = h.getProvince();

            if (loc != null) {
                province = loc.province; // tỉnh/TP đúng sau sáp nhập
                if (blank(street)) street = pick(loc.streets, h);
                if (!isRealWard(ward)) ward = pick(loc.wards, h); // thay phường giả/rỗng bằng phường thật
            } else if (blank(province)) {
                province = h.getCity(); // không map được tỉnh -> giữ tạm theo city (chỉ bỏ quận/huyện)
            }

            h.setHouseNumber(house);
            h.setStreet(street);
            h.setWard(ward);
            h.setProvince(province);
            h.setAddress(HotelSupport.composeAddress(house, street, ward, null, province, h.getCity()));
            toSave.add(h);
        }

        if (!toSave.isEmpty()) {
            hotelRepository.saveAll(toSave);
        }
        log.info("[HotelAddressBackfill] Đã chuẩn hoá địa chỉ {} khách sạn cũ về format VN mới (số nhà, đường, phường/xã, tỉnh).",
                toSave.size());
    }

    // ----- logic nhận diện cần sửa -----

    private boolean needsFix(Hotel h) {
        // KS seed hàng loạt (externalId >= 800000) đã chuẩn -> bỏ qua.
        if (h.getExternalId() != null && h.getExternalId() >= HotelBulkSeeder.EXT_BASE) return false;

        boolean hasDistrict = !blank(h.getDistrict());
        VnLocations.Loc loc = VnLocations.byCity(h.getCity());
        if (loc == null) {
            // Không biết tỉnh đúng -> chỉ cần sửa nếu còn dính quận/huyện.
            return hasDistrict;
        }
        boolean provinceWrong = !loc.province.equalsIgnoreCase(safe(h.getProvince()));
        boolean wardGeneric = !isRealWard(h.getWard());
        return hasDistrict || provinceWrong || wardGeneric;
    }

    /** Phường/xã "thật": không rỗng và không phải dạng giả "Phường &lt;số&gt;" / "Quận ...". */
    private static boolean isRealWard(String w) {
        if (blank(w)) return false;
        String s = w.trim();
        if (s.matches("(?i)^phường\\s*\\d+$")) return false; // "Phường 5" (giả) -> cần thay
        if (s.matches("(?i)^quận.*")) return false;          // dính cấp quận -> cần thay
        return true;
    }

    /** Chọn phần tử tất định theo id (ổn định khi chạy lại). */
    private static String pick(String[] arr, Hotel h) {
        long id = h.getId() == null ? 0 : h.getId();
        return arr[(int) Math.floorMod(id, arr.length)];
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String safe(String s) { return s == null ? "" : s; }
}
