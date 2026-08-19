package com.dididi.booking.hotel.domain;

import com.dididi.booking.hotel.domain.enums.Region;

import java.util.LinkedHashMap;
import java.util.Map;

/** Toạ độ trung tâm + vùng miền của các thành phố du lịch chính (cho seed + backfill demo). */
public final class CityGeo {

    private CityGeo() {}

    public record Geo(double lat, double lng, Region region) {}

    private static final Map<String, Geo> M = new LinkedHashMap<>();
    static {
        M.put("TP.HCM",    new Geo(10.7769, 106.7009, Region.SOUTH));
        M.put("Hà Nội",    new Geo(21.0278, 105.8342, Region.NORTH));
        M.put("Đà Nẵng",   new Geo(16.0544, 108.2022, Region.CENTRAL));
        M.put("Nha Trang", new Geo(12.2388, 109.1967, Region.CENTRAL));
        M.put("Huế",       new Geo(16.4637, 107.5909, Region.CENTRAL));
        M.put("Phú Quốc",  new Geo(10.2899, 103.9840, Region.SOUTH));
        M.put("Đà Lạt",    new Geo(11.9404, 108.4583, Region.CENTRAL));
        M.put("Hội An",    new Geo(15.8801, 108.3380, Region.CENTRAL));
        M.put("Vũng Tàu",  new Geo(10.4114, 107.1362, Region.SOUTH));
        M.put("Hạ Long",   new Geo(20.9101, 107.1839, Region.NORTH));
        M.put("Cần Thơ",   new Geo(10.0452, 105.7469, Region.SOUTH));
        M.put("Sa Pa",     new Geo(22.3380, 103.8440, Region.NORTH));
        // 24 tỉnh/thành còn lại (18/08/2026) — phủ đủ 34 đơn vị cấp tỉnh sau sáp nhập
        M.put("Hải Phòng",     new Geo(20.8449, 106.6881, Region.NORTH));
        M.put("Hà Giang",      new Geo(22.8233, 104.9836, Region.NORTH));
        M.put("Thái Nguyên",   new Geo(21.5928, 105.8442, Region.NORTH));
        M.put("Việt Trì",      new Geo(21.3100, 105.4020, Region.NORTH));
        M.put("Bắc Ninh",      new Geo(21.1861, 106.0763, Region.NORTH));
        M.put("Hưng Yên",      new Geo(20.6464, 106.0512, Region.NORTH));
        M.put("Ninh Bình",     new Geo(20.2506, 105.9745, Region.NORTH));
        M.put("Sầm Sơn",       new Geo(19.7472, 105.9040, Region.CENTRAL));
        M.put("Vinh",          new Geo(18.6796, 105.6813, Region.CENTRAL));
        M.put("Hà Tĩnh",       new Geo(18.3428, 105.9057, Region.CENTRAL));
        M.put("Đồng Hới",      new Geo(17.4659, 106.5983, Region.CENTRAL));
        M.put("Quảng Ngãi",    new Geo(15.1214, 108.8044, Region.CENTRAL));
        M.put("Quy Nhơn",      new Geo(13.7820, 109.2191, Region.CENTRAL));
        M.put("Buôn Ma Thuột", new Geo(12.6797, 108.0382, Region.CENTRAL));
        M.put("Biên Hòa",      new Geo(10.9508, 106.8221, Region.SOUTH));
        M.put("Tây Ninh",      new Geo(11.3100, 106.0980, Region.SOUTH));
        M.put("Mỹ Tho",        new Geo(10.3600, 106.3600, Region.SOUTH));
        M.put("Vĩnh Long",     new Geo(10.2537, 105.9722, Region.SOUTH));
        M.put("Cà Mau",        new Geo(9.1769, 105.1524, Region.SOUTH));
        M.put("Điện Biên Phủ", new Geo(21.3860, 103.0230, Region.NORTH));
        M.put("Lai Châu",      new Geo(22.3964, 103.4590, Region.NORTH));
        M.put("Mộc Châu",      new Geo(20.8460, 104.6380, Region.NORTH));
        M.put("Lạng Sơn",      new Geo(21.8537, 106.7615, Region.NORTH));
        M.put("Cao Bằng",      new Geo(22.6657, 106.2570, Region.NORTH));
    }

    /** Toàn bộ bảng (chỉ đọc) — để bơm xuống JS trang vendor-register (bản đồ tự bay tới TP vừa gõ). */
    public static Map<String, Geo> all() {
        return java.util.Collections.unmodifiableMap(M);
    }

    /** Tra cứu theo tên thành phố (khớp gần đúng, không phân biệt hoa/thường); không thấy -> null. */
    public static Geo lookup(String city) {
        if (city == null || city.isBlank()) return null;
        String c = city.trim().toLowerCase();
        Geo exact = M.get(city.trim());
        if (exact != null) return exact;
        for (Map.Entry<String, Geo> e : M.entrySet()) {
            String k = e.getKey().toLowerCase();
            if (c.contains(k) || k.contains(c)) return e.getValue();
        }
        return null;
    }
}
