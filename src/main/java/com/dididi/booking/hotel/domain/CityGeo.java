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
