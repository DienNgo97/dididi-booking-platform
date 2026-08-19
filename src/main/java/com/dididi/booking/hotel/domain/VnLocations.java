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


    /**
     * 24 TỈNH/THÀNH CÒN LẠI (bổ sung 18/08/2026) — cùng 12 điểm trong ALL phủ đủ 34 đơn vị cấp tỉnh
     * sau sáp nhập 1/7/2025. Mỗi tỉnh chọn tỉnh lỵ hoặc điểm du lịch tiêu biểu làm "city".
     * Phường lấy theo quy ước đặt tên sau sáp nhập (nhiều phường trung tâm mang tên TP cũ);
     * một số tên đã đối chiếu báo chí (Hạc Thành, Thành Sen, Phố Hiến, Trấn Biên, Thục Phán...).
     * weight = 3 KS/điểm (tổng 72) — dùng bởi HotelBulkSeeder phase 2 (app.seed.hotels34).
     */
    public static final List<Loc> EXTRA = List.of(
        new Loc("Hải Phòng", "TP. Hải Phòng", Region.NORTH, 20.8449, 106.6881,
            new String[]{"Phường Hồng Bàng", "Phường Lê Chân", "Phường Ngô Quyền", "Phường Đồ Sơn"},
            new String[]{"Điện Biên Phủ", "Lạch Tray", "Trần Phú", "Tô Hiệu", "Cầu Đất", "Văn Cao"}, 3),
        new Loc("Hà Giang", "Tỉnh Tuyên Quang", Region.NORTH, 22.8233, 104.9836,
            new String[]{"Phường Hà Giang 1", "Phường Hà Giang 2"},
            new String[]{"Nguyễn Trãi", "Trần Phú", "Lý Tự Trọng", "Nguyễn Thái Học"}, 3),
        new Loc("Thái Nguyên", "Tỉnh Thái Nguyên", Region.NORTH, 21.5928, 105.8442,
            new String[]{"Phường Phan Đình Phùng", "Phường Gia Sàng", "Phường Quyết Thắng"},
            new String[]{"Hoàng Văn Thụ", "Lương Ngọc Quyến", "Cách Mạng Tháng Tám", "Bến Tượng"}, 3),
        new Loc("Việt Trì", "Tỉnh Phú Thọ", Region.NORTH, 21.3100, 105.4020,
            new String[]{"Phường Việt Trì", "Phường Nông Trang", "Phường Vân Phú"},
            new String[]{"Hùng Vương", "Trần Phú", "Nguyễn Tất Thành", "Châu Phong"}, 3),
        new Loc("Bắc Ninh", "Tỉnh Bắc Ninh", Region.NORTH, 21.1861, 106.0763,
            new String[]{"Phường Kinh Bắc", "Phường Vũ Ninh", "Phường Đại Phúc"},
            new String[]{"Lý Thái Tổ", "Nguyễn Gia Thiều", "Trần Hưng Đạo", "Ngô Gia Tự"}, 3),
        new Loc("Hưng Yên", "Tỉnh Hưng Yên", Region.NORTH, 20.6464, 106.0512,
            new String[]{"Phường Phố Hiến", "Phường Sơn Nam"},
            new String[]{"Điện Biên", "Trưng Trắc", "Nguyễn Văn Linh", "Bãi Sậy"}, 3),
        new Loc("Ninh Bình", "Tỉnh Ninh Bình", Region.NORTH, 20.2506, 105.9745,
            new String[]{"Phường Hoa Lư", "Phường Tây Hoa Lư", "Phường Nam Hoa Lư"},
            new String[]{"Trần Hưng Đạo", "Lương Văn Thăng", "Đinh Tiên Hoàng", "Tràng An"}, 3),
        new Loc("Sầm Sơn", "Tỉnh Thanh Hóa", Region.CENTRAL, 19.7472, 105.9040,
            new String[]{"Phường Sầm Sơn", "Phường Nam Sầm Sơn", "Phường Hạc Thành"},
            new String[]{"Hồ Xuân Hương", "Lê Lợi", "Nguyễn Du", "Thanh Niên"}, 3),
        new Loc("Vinh", "Tỉnh Nghệ An", Region.CENTRAL, 18.6796, 105.6813,
            new String[]{"Phường Thành Vinh", "Phường Trường Vinh", "Phường Cửa Lò"},
            new String[]{"Quang Trung", "Lê Lợi", "Nguyễn Thị Minh Khai", "Bình Minh"}, 3),
        new Loc("Hà Tĩnh", "Tỉnh Hà Tĩnh", Region.CENTRAL, 18.3428, 105.9057,
            new String[]{"Phường Thành Sen", "Phường Trần Phú"},
            new String[]{"Phan Đình Phùng", "Trần Phú", "Hải Thượng Lãn Ông", "Nguyễn Công Trứ"}, 3),
        new Loc("Đồng Hới", "Tỉnh Quảng Trị", Region.CENTRAL, 17.4659, 106.5983,
            new String[]{"Phường Đồng Hới", "Phường Đồng Thuận"},
            new String[]{"Trần Hưng Đạo", "Lý Thường Kiệt", "Nguyễn Hữu Cảnh", "Trương Pháp"}, 3),
        new Loc("Quảng Ngãi", "Tỉnh Quảng Ngãi", Region.CENTRAL, 15.1214, 108.8044,
            new String[]{"Phường Cẩm Thành", "Phường Nghĩa Lộ"},
            new String[]{"Quang Trung", "Lê Trung Đình", "Hùng Vương", "Phan Đình Phùng"}, 3),
        new Loc("Quy Nhơn", "Tỉnh Gia Lai", Region.CENTRAL, 13.7820, 109.2191,
            new String[]{"Phường Quy Nhơn", "Phường Quy Nhơn Nam", "Phường Quy Nhơn Đông"},
            new String[]{"An Dương Vương", "Xuân Diệu", "Nguyễn Tất Thành", "Trần Hưng Đạo"}, 3),
        new Loc("Buôn Ma Thuột", "Tỉnh Đắk Lắk", Region.CENTRAL, 12.6797, 108.0382,
            new String[]{"Phường Buôn Ma Thuột", "Phường Tân An", "Phường Tuy Hòa"},
            new String[]{"Lê Duẩn", "Phan Chu Trinh", "Nguyễn Tất Thành", "Y Jút"}, 3),
        new Loc("Biên Hòa", "Tỉnh Đồng Nai", Region.SOUTH, 10.9508, 106.8221,
            new String[]{"Phường Trấn Biên", "Phường Tam Hiệp", "Phường Long Bình"},
            new String[]{"Võ Thị Sáu", "Phạm Văn Thuận", "30 Tháng 4", "Nguyễn Ái Quốc"}, 3),
        new Loc("Tây Ninh", "Tỉnh Tây Ninh", Region.SOUTH, 11.3100, 106.0980,
            new String[]{"Phường Tân Ninh", "Phường Long Hoa"},
            new String[]{"30 Tháng 4", "Cách Mạng Tháng Tám", "Trần Hưng Đạo", "Võ Thị Sáu"}, 3),
        new Loc("Mỹ Tho", "Tỉnh Đồng Tháp", Region.SOUTH, 10.3600, 106.3600,
            new String[]{"Phường Mỹ Tho", "Phường Đạo Thạnh"},
            new String[]{"Trưng Trắc", "Ấp Bắc", "Lê Lợi", "Nam Kỳ Khởi Nghĩa"}, 3),
        new Loc("Vĩnh Long", "Tỉnh Vĩnh Long", Region.SOUTH, 10.2537, 105.9722,
            new String[]{"Phường Long Châu", "Phường Phước Hậu"},
            new String[]{"Phạm Thái Bường", "30 Tháng 4", "Nguyễn Huệ", "Trưng Nữ Vương"}, 3),
        new Loc("Cà Mau", "Tỉnh Cà Mau", Region.SOUTH, 9.1769, 105.1524,
            new String[]{"Phường An Xuyên", "Phường Tân Thành"},
            new String[]{"Phan Ngọc Hiển", "Nguyễn Tất Thành", "Trần Hưng Đạo", "Lý Thường Kiệt"}, 3),
        new Loc("Điện Biên Phủ", "Tỉnh Điện Biên", Region.NORTH, 21.3860, 103.0230,
            new String[]{"Phường Điện Biên Phủ", "Phường Mường Thanh"},
            new String[]{"Võ Nguyên Giáp", "7 Tháng 5", "Hoàng Văn Thái", "Trường Chinh"}, 3),
        new Loc("Lai Châu", "Tỉnh Lai Châu", Region.NORTH, 22.3964, 103.4590,
            new String[]{"Phường Tân Phong", "Phường Đoàn Kết"},
            new String[]{"Trần Hưng Đạo", "Lê Duẩn", "Hoàng Văn Thái", "Điện Biên Phủ"}, 3),
        new Loc("Mộc Châu", "Tỉnh Sơn La", Region.NORTH, 20.8460, 104.6380,
            new String[]{"Phường Mộc Châu", "Phường Mộc Sơn"},
            new String[]{"Hoàng Quốc Việt", "Tây Tiến", "Chu Văn Thịnh", "Trần Huy Liệu"}, 3),
        new Loc("Lạng Sơn", "Tỉnh Lạng Sơn", Region.NORTH, 21.8537, 106.7615,
            new String[]{"Phường Kỳ Lừa", "Phường Tam Thanh", "Phường Đông Kinh"},
            new String[]{"Trần Đăng Ninh", "Lê Lợi", "Bà Triệu", "Hùng Vương"}, 3),
        new Loc("Cao Bằng", "Tỉnh Cao Bằng", Region.NORTH, 22.6657, 106.2570,
            new String[]{"Phường Thục Phán", "Phường Nùng Trí Cao"},
            new String[]{"Kim Đồng", "Vườn Cam", "Hoàng Đình Giong", "Xuân Trường"}, 3)
    );

    /** Tra cứu địa danh theo tên thành phố (khớp gần đúng, không phân biệt hoa/thường); không thấy → null. */
    public static Loc byCity(String city) {
        if (city == null || city.isBlank()) return null;
        String c = city.trim().toLowerCase(Locale.ROOT);
        java.util.List<Loc> both = new java.util.ArrayList<>(ALL);
        both.addAll(EXTRA);
        for (Loc l : both) {
            if (l.city.toLowerCase(Locale.ROOT).equals(c)) return l;
        }
        for (Loc l : both) {
            String k = l.city.toLowerCase(Locale.ROOT);
            if (c.contains(k) || k.contains(c)) return l;
        }
        return null;
    }
}
