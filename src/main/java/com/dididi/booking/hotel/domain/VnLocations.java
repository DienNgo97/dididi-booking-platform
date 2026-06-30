package com.dididi.booking.hotel.domain;

import com.dididi.booking.hotel.domain.enums.Region;

import java.util.List;
import java.util.Locale;

/**
 * Dữ liệu địa danh Việt Nam SAU sắp xếp đơn vị hành chính 1/7/2025
 * (Nghị quyết 202/2025/QH15 + các Nghị quyết UBTVQH): mô hình 2 cấp TỈNH → PHƯỜNG/XÃ,
 * KHÔNG còn cấp quận/huyện. Cả nước còn 34 đơn vị cấp tỉnh.
 *
 * Dùng để:
 *   - Seed 300 khách sạn mới với địa chỉ đúng format VN: số nhà, đường, phường/xã, tỉnh/TP.
 *   - Backfill (chuẩn hoá) địa chỉ các khách sạn cũ về đúng format trên.
 *
 * Tên phường/xã lấy từ nguồn chính thức (chinhphu.vn / Nghị quyết UBTVQH / báo Tuổi Trẻ),
 * ưu tiên các phường trung tâm/khu du lịch nơi tập trung khách sạn.
 *
 * LƯU Ý các thay đổi tỉnh sau sáp nhập có trong dữ liệu này:
 *   - Vũng Tàu  → thuộc TP. Hồ Chí Minh (hợp nhất Bà Rịa - Vũng Tàu + Bình Dương vào TP.HCM)
 *   - Hội An    → thuộc TP. Đà Nẵng     (hợp nhất Quảng Nam vào Đà Nẵng)
 *   - Nha Trang → thuộc tỉnh Khánh Hòa  (Khánh Hòa + Ninh Thuận)
 *   - Đà Lạt    → thuộc tỉnh Lâm Đồng   (Lâm Đồng + Đắk Nông + Bình Thuận)
 *   - Phú Quốc  → ĐẶC KHU, thuộc tỉnh An Giang (Kiên Giang + An Giang)
 *   - Sa Pa     → thuộc tỉnh Lào Cai    (Lào Cai + Yên Bái)
 *   - Hạ Long   → thuộc tỉnh Quảng Ninh (không sáp nhập)
 */
public final class VnLocations {

    private VnLocations() {}

    /** Một điểm đến: thành phố hiển thị + tỉnh/TP (sau sáp nhập) + toạ độ + phường/xã + đường thật. */
    public static final class Loc {
        public final String city;        // tên thành phố/điểm đến — giữ ở Hotel.city để gom nhóm + tra geo
        public final String province;    // tỉnh/TP trực thuộc TW (đặt vào Hotel.province) — ĐÚNG sau sáp nhập
        public final Region region;
        public final double lat, lng;    // toạ độ trung tâm (jitter quanh đây cho từng KS)
        public final String[] wards;     // phường/xã THẬT sau 1/7/2025
        public final String[] streets;   // tên đường thật phổ biến của thành phố
        public final int weight;         // số khách sạn seed phân bổ cho thành phố này (tổng = 300)

        Loc(String city, String province, Region region, double lat, double lng,
            String[] wards, String[] streets, int weight) {
            this.city = city; this.province = province; this.region = region;
            this.lat = lat; this.lng = lng; this.wards = wards; this.streets = streets; this.weight = weight;
        }
    }

    /** 12 điểm đến du lịch chính. Tổng weight = 300 (số KS seed). */
    public static final List<Loc> ALL = List.of(
        new Loc("TP.HCM", "TP. Hồ Chí Minh", Region.SOUTH, 10.7769, 106.7009,
            new String[]{"Phường Sài Gòn", "Phường Bến Thành", "Phường Tân Định", "Phường Cầu Ông Lãnh",
                         "Phường Bàn Cờ", "Phường Cầu Kiệu", "Phường Xuân Hòa", "Phường Nhiêu Lộc",
                         "Phường Khánh Hội", "Phường Chợ Quán"},
            new String[]{"Đồng Khởi", "Nguyễn Huệ", "Lê Lợi", "Pasteur", "Hai Bà Trưng", "Lê Thánh Tôn",
                         "Nguyễn Thị Minh Khai", "Cách Mạng Tháng Tám", "Nam Kỳ Khởi Nghĩa", "Điện Biên Phủ"},
            40),
        new Loc("Hà Nội", "TP. Hà Nội", Region.NORTH, 21.0278, 105.8342,
            new String[]{"Phường Hoàn Kiếm", "Phường Cửa Nam", "Phường Ba Đình", "Phường Hồng Hà",
                         "Phường Văn Miếu - Quốc Tử Giám", "Phường Ngọc Hà", "Phường Giảng Võ",
                         "Phường Hai Bà Trưng", "Phường Tây Hồ"},
            new String[]{"Hàng Bài", "Tràng Tiền", "Lý Thường Kiệt", "Hai Bà Trưng", "Trần Hưng Đạo",
                         "Phố Huế", "Bà Triệu", "Hàng Khay", "Lý Thái Tổ", "Nhà Thờ"},
            35),
        new Loc("Đà Nẵng", "TP. Đà Nẵng", Region.CENTRAL, 16.0544, 108.2022,
            new String[]{"Phường Hải Châu", "Phường Hòa Cường", "Phường Thanh Khê", "Phường An Hải",
                         "Phường Sơn Trà", "Phường Ngũ Hành Sơn", "Phường Cẩm Lệ", "Phường Hòa Xuân"},
            new String[]{"Võ Nguyên Giáp", "Bạch Đằng", "Trần Phú", "Nguyễn Văn Linh", "Phạm Văn Đồng",
                         "Hồ Nghinh", "Hùng Vương", "Hoàng Sa", "Lê Duẩn", "2 Tháng 9"},
            30),
        new Loc("Nha Trang", "Tỉnh Khánh Hòa", Region.CENTRAL, 12.2388, 109.1967,
            new String[]{"Phường Nha Trang", "Phường Nam Nha Trang", "Phường Bắc Nha Trang", "Phường Tây Nha Trang"},
            new String[]{"Trần Phú", "Nguyễn Thị Minh Khai", "Hùng Vương", "Biệt Thự", "Trần Quang Khải",
                         "Nguyễn Thiện Thuật", "Lê Thánh Tôn", "Hoàng Hoa Thám", "Nguyễn Trãi", "Yersin"},
            28),
        new Loc("Phú Quốc", "Tỉnh An Giang", Region.SOUTH, 10.2899, 103.9840,
            new String[]{"Đặc khu Phú Quốc"},
            new String[]{"Trần Hưng Đạo", "Nguyễn Trung Trực", "Bà Kèo", "Trần Phú", "Hùng Vương",
                         "30 Tháng 4", "Nguyễn Văn Cừ", "Dương Đông"},
            28),
        new Loc("Đà Lạt", "Tỉnh Lâm Đồng", Region.CENTRAL, 11.9404, 108.4583,
            new String[]{"Phường Xuân Hương - Đà Lạt", "Phường Cam Ly - Đà Lạt", "Phường Lâm Viên - Đà Lạt",
                         "Phường Xuân Trường - Đà Lạt", "Phường Lang Biang - Đà Lạt"},
            new String[]{"Nguyễn Chí Thanh", "Phan Đình Phùng", "Bùi Thị Xuân", "Trần Phú", "Hồ Tùng Mậu",
                         "Nguyễn Văn Cừ", "Yersin", "Trần Hưng Đạo", "Ba Tháng Hai", "Khe Sanh"},
            25),
        new Loc("Hạ Long", "Tỉnh Quảng Ninh", Region.NORTH, 20.9101, 107.1839,
            new String[]{"Phường Bãi Cháy", "Phường Hồng Gai", "Phường Hạ Long", "Phường Tuần Châu",
                         "Phường Hà Tu", "Phường Hà Lầm", "Phường Cao Xanh", "Phường Việt Hưng"},
            new String[]{"Hạ Long", "Hoàng Quốc Việt", "Hậu Cần", "Lê Thánh Tông", "Trần Hưng Đạo",
                         "Hùng Thắng", "Vườn Đào", "Anh Đào"},
            22),
        new Loc("Vũng Tàu", "TP. Hồ Chí Minh", Region.SOUTH, 10.4114, 107.1362,
            new String[]{"Phường Vũng Tàu", "Phường Tam Thắng", "Phường Rạch Dừa", "Phường Phước Thắng"},
            new String[]{"Thùy Vân", "Hạ Long", "Trần Phú", "Hoàng Hoa Thám", "Lê Hồng Phong",
                         "Nguyễn An Ninh", "Ba Cu", "Quang Trung", "Trương Công Định", "Lê Lợi"},
            22),
        new Loc("Hội An", "TP. Đà Nẵng", Region.CENTRAL, 15.8801, 108.3380,
            new String[]{"Phường Hội An", "Phường Hội An Đông", "Phường Hội An Tây"},
            new String[]{"Trần Phú", "Nguyễn Thái Học", "Bạch Đằng", "Lê Lợi", "Cửa Đại", "Hai Bà Trưng",
                         "Nguyễn Phúc Chu", "Trần Hưng Đạo", "Lý Thường Kiệt", "Thái Phiên"},
            20),
        new Loc("Huế", "TP. Huế", Region.CENTRAL, 16.4637, 107.5909,
            new String[]{"Phường Phú Xuân", "Phường Thuận Hóa", "Phường Kim Long", "Phường Vỹ Dạ",
                         "Phường Thủy Xuân", "Phường An Cựu"},
            new String[]{"Lê Lợi", "Hùng Vương", "Trần Hưng Đạo", "Bà Triệu", "Nguyễn Huệ", "Phạm Ngũ Lão",
                         "Chu Văn An", "Võ Thị Sáu", "Đội Cung", "Hai Bà Trưng"},
            18),
        new Loc("Sa Pa", "Tỉnh Lào Cai", Region.NORTH, 22.3380, 103.8440,
            new String[]{"Phường Sa Pa", "Xã Tả Van", "Xã Mường Bo", "Xã Bản Hồ", "Xã Tả Phìn", "Xã Ngũ Chỉ Sơn"},
            new String[]{"Cầu Mây", "Fansipan", "Mường Hoa", "Thạch Sơn", "Hoàng Liên", "Đông Lợi",
                         "Ngũ Chỉ Sơn", "Violet", "Hàm Rồng", "Xuân Viên"},
            17),
        new Loc("Cần Thơ", "TP. Cần Thơ", Region.SOUTH, 10.0452, 105.7469,
            new String[]{"Phường Ninh Kiều", "Phường Cái Khế", "Phường Tân An", "Phường An Bình", "Phường Cái Răng"},
            new String[]{"Hai Bà Trưng", "Nguyễn Trãi", "Trần Văn Khéo", "30 Tháng 4", "Hòa Bình",
                         "Phan Đình Phùng", "Nguyễn An Ninh", "Châu Văn Liêm", "Lý Tự Trọng", "Đề Thám"},
            15)
    );

    /** Tra cứu địa danh theo tên thành phố (khớp gần đúng, không phân biệt hoa/thường); không thấy → null. */
    public static Loc byCity(String city) {
        if (city == null || city.isBlank()) return null;
        String c = city.trim().toLowerCase(Locale.ROOT);
        for (Loc l : ALL) {
            if (l.city.toLowerCase(Locale.ROOT).equals(c)) return l;
        }
        for (Loc l : ALL) {
            String k = l.city.toLowerCase(Locale.ROOT);
            if (c.contains(k) || k.contains(c)) return l;
        }
        return null;
    }
}
