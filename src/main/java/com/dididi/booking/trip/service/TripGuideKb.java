package com.dididi.booking.trip.service;

import java.util.List;

/**
 * TRI THỨC NỘI BỘ (KB) cho AI hướng dẫn viên du lịch — 12 thành phố du lịch (trùng bộ VnLocations).
 *
 * Vai trò trong kiến trúc hybrid (giống chatbot CSKH):
 *  - KHÔNG có API key LLM: toàn bộ câu trả lời (phương tiện, ăn uống, vui chơi, lịch trình theo giờ)
 *    được sinh từ dữ liệu này -> demo chạy offline, không tốn phí.
 *  - CÓ API key: dữ liệu này được đưa vào prompt làm CĂN CỨ (grounding) để LLM không bịa giá/số liệu.
 *
 * Ghi chú dữ liệu: giá chỉ là KHOẢNG THAM KHẢO cho đồ án; slot = buổi phù hợp nhất (sang/chieu/toi/any);
 * minutes = thời gian tham quan dự kiến, dùng để xếp lịch trình theo mốc giờ.
 */
final class TripGuideKb {

    /** Điểm tham quan / vui chơi: thời lượng dự kiến + buổi phù hợp để xếp lịch. */
    record Spot(String name, int minutes, String slot, String note) {}

    /** Món ăn / khu ăn uống đặc trưng. */
    record Eat(String dish, String where, String price) {}

    /** Tri thức 1 thành phố. aliases: dạng KHÔNG DẤU, lowercase, so khớp theo từ. */
    record CityGuide(String key, String name, List<String> aliases,
                     String transport, List<Eat> eats, List<Spot> spots, String tips) {}

    private TripGuideKb() {}

    static final List<CityGuide> CITIES = List.of(

        new CityGuide("hanoi", "Hà Nội", List.of("ha noi", "hanoi"),
            """
            • Xe bus: mạng lưới dày nhất nước, 7.000–9.000đ/lượt; tuyến 86 đi thẳng sân bay Nội Bài.
            • Tàu điện trên cao: tuyến Cát Linh–Hà Đông và Nhổn–Ga Hà Nội, vé 8.000–15.000đ — nên thử cho biết.
            • Taxi/Grab: nhiều, từ Nội Bài về phố cổ khoảng 250.000–350.000đ.
            • Thuê xe máy: 100.000–150.000đ/ngày; lưu ý phố cổ đông và nhiều đường một chiều.
            • Xích lô + đi bộ là cách hay nhất để dạo 36 phố phường.""",
            List.of(new Eat("Phở bò", "Phở Thìn Lò Đúc, phở Bát Đàn", "40.000–70.000đ"),
                    new Eat("Bún chả", "Hàng Quạt, Đắc Kim Hàng Mành", "40.000–60.000đ"),
                    new Eat("Chả cá Lã Vọng", "phố Chả Cá", "120.000–180.000đ"),
                    new Eat("Bánh mì + cà phê trứng", "Giảng, Đinh", "25.000–50.000đ"),
                    new Eat("Bia hơi + đồ nhắm", "ngã tư Tạ Hiện buổi tối", "50.000–150.000đ")),
            List.of(new Spot("Hồ Gươm + đền Ngọc Sơn", 90, "sang", "đi sớm cho mát, vé đền 30.000đ"),
                    new Spot("Lăng Bác + quảng trường Ba Đình", 120, "sang", "chỉ mở buổi sáng, nghỉ thứ 2 & thứ 6"),
                    new Spot("Văn Miếu – Quốc Tử Giám", 90, "sang", "vé 70.000đ"),
                    new Spot("Dạo 36 phố phường + chợ Đồng Xuân", 120, "any", "đi bộ hoặc xích lô"),
                    new Spot("Hoàng thành Thăng Long", 120, "chieu", "vé 100.000đ"),
                    new Spot("Nhà tù Hoả Lò", 75, "chieu", "vé 50.000đ, thuyết minh hay"),
                    new Spot("Hồ Tây — đạp xe, ngắm hoàng hôn", 90, "chieu", "thuê xe đạp quanh hồ"),
                    new Spot("Múa rối nước Thăng Long", 60, "toi", "nên mua vé trước, 100.000–200.000đ"),
                    new Spot("Phố đi bộ + bia Tạ Hiện", 150, "toi", "cuối tuần phố đi bộ quanh Hồ Gươm")),
            "Mùa đẹp: thu (9–11) và xuân. Phố cổ đông — giữ đồ cẩn thận. Cuối tuần có phố đi bộ Hồ Gươm."),

        new CityGuide("hcm", "TP. Hồ Chí Minh", List.of("tp.hcm", "tphcm", "ho chi minh", "sai gon", "saigon", "hcm"),
            """
            • Metro số 1 (Bến Thành–Suối Tiên): 7.000–20.000đ/lượt — mới, sạch, nên trải nghiệm.
            • Xe bus: 5.000–7.000đ/lượt, phủ rộng; buýt sông Sài Gòn 15.000đ ngắm thành phố từ sông.
            • Taxi/Grab: rẻ và nhiều; từ Tân Sơn Nhất về Quận 1 khoảng 150.000–250.000đ.
            • Thuê xe máy: 120.000–180.000đ/ngày; giờ cao điểm kẹt xe, tính dư thời gian di chuyển.""",
            List.of(new Eat("Cơm tấm sườn bì chả", "Ba Ghiền (Đặng Văn Ngữ)", "50.000–80.000đ"),
                    new Eat("Bánh mì", "Huỳnh Hoa, Bánh mì 37", "30.000–60.000đ"),
                    new Eat("Hủ tiếu Nam Vang", "Thành Đạt, Nhân Quán", "50.000–80.000đ"),
                    new Eat("Ốc + hải sản đêm", "khu Vĩnh Khánh (Q4)", "150.000–300.000đ"),
                    new Eat("Cà phê sữa đá bệt", "công viên 30/4, chung cư 42 Nguyễn Huệ", "20.000–60.000đ")),
            List.of(new Spot("Dinh Độc Lập", 90, "sang", "vé 65.000đ"),
                    new Spot("Nhà thờ Đức Bà + Bưu điện Thành phố", 60, "sang", "sát nhau, chụp ảnh đẹp"),
                    new Spot("Bảo tàng Chứng tích Chiến tranh", 90, "chieu", "vé 40.000đ"),
                    new Spot("Chợ Bến Thành", 75, "any", "trả giá khi mua đồ lưu niệm"),
                    new Spot("Chợ Lớn + chùa Bà Thiên Hậu", 120, "chieu", "khu người Hoa, Q5"),
                    new Spot("Phố đi bộ Nguyễn Huệ", 90, "toi", "cuối tuần đông vui"),
                    new Spot("Ngắm thành phố từ Landmark 81 / Bitexco", 90, "toi", "vé đài quan sát 200.000–400.000đ"),
                    new Spot("Phố Bùi Viện", 120, "toi", "phố tây, ồn ào náo nhiệt")),
            "Nắng quanh năm, mưa rào nhanh tạnh (5–10). Mang áo mưa mỏng. Cảnh giác móc túi nơi đông người."),

        new CityGuide("danang", "Đà Nẵng", List.of("da nang", "danang"),
            """
            • Thuê xe máy: 100.000–150.000đ/ngày — tiện nhất, đường rộng dễ đi, sân bay cách trung tâm ~10 phút.
            • Taxi/Grab: nhiều, giá hợp lý; đi Bà Nà nên đặt xe 7 chỗ khứ hồi (~600.000–800.000đ).
            • Xe đạp công cộng TNGo: điểm thuê dọc sông Hàn và biển.
            • Đi Hội An: taxi/xe buýt ~45 phút, hoặc thuê xe máy chạy đường ven biển rất đẹp.""",
            List.of(new Eat("Mì Quảng", "Mì Quảng 1A Hải Phòng", "35.000–60.000đ"),
                    new Eat("Bún chả cá", "109 Nguyễn Chí Thanh", "30.000–50.000đ"),
                    new Eat("Bánh tráng cuốn thịt heo", "Trần (nhiều chi nhánh)", "80.000–150.000đ"),
                    new Eat("Hải sản", "dọc biển Võ Nguyên Giáp, Bé Mặn", "200.000–500.000đ"),
                    new Eat("Bánh xèo + nem lụi", "Bà Dưỡng (k280 Hoàng Diệu)", "50.000–100.000đ")),
            List.of(new Spot("Bà Nà Hills + Cầu Vàng", 300, "sang", "trọn buổi; vé cáp ~900.000đ, đi sớm tránh đông"),
                    new Spot("Ngũ Hành Sơn", 120, "sang", "vé 40.000đ + thang máy; leo bậc đá, mang giày êm"),
                    new Spot("Bán đảo Sơn Trà + chùa Linh Ứng", 120, "chieu", "đường ven biển đẹp, cẩn thận dốc"),
                    new Spot("Tắm biển Mỹ Khê", 120, "any", "sáng sớm hoặc 16h trở đi"),
                    new Spot("Bảo tàng điêu khắc Chăm", 75, "chieu", "vé 60.000đ"),
                    new Spot("Cầu Rồng phun lửa + cầu Tình Yêu", 60, "toi", "phun lửa 21:00 tối thứ 7 & CN"),
                    new Spot("Chợ đêm Sơn Trà", 90, "toi", "gần cầu Rồng, ăn vặt hải sản")),
            "Mùa đẹp: 3–8 (biển êm). 10–12 hay mưa bão. Kết hợp Hội An 1 ngày rất tiện."),

        new CityGuide("hoian", "Hội An", List.of("hoi an", "hoian"),
            """
            • Phố cổ CẤM Ô TÔ nhiều khung giờ — đi bộ + xe đạp là chính (nhiều khách sạn tặng xe đạp).
            • Xích lô dạo phố cổ: ~150.000đ/30 phút.
            • Từ Đà Nẵng: taxi ~350.000–450.000đ (45 phút) hoặc bus.
            • Thuê xe máy 100.000–130.000đ/ngày nếu muốn đi rừng dừa, làng nghề, biển.""",
            List.of(new Eat("Cao lầu", "Thanh Cao Lầu, chợ Hội An", "30.000–50.000đ"),
                    new Eat("Cơm gà", "Bà Buội, Bà Nga", "40.000–60.000đ"),
                    new Eat("Bánh mì", "Phượng, Madam Khánh", "25.000–45.000đ"),
                    new Eat("Mót Hội An (nước thảo mộc)", "phố Trần Phú", "15.000đ"),
                    new Eat("Chè bắp + bánh đập", "Cẩm Nam", "15.000–30.000đ")),
            List.of(new Spot("Rừng dừa Bảy Mẫu — thúng chai", 120, "sang", "vé + thúng ~150.000–200.000đ/người"),
                    new Spot("Làng gốm Thanh Hà", 90, "sang", "tự nặn gốm, vé 35.000đ"),
                    new Spot("Biển An Bàng", 150, "any", "bãi biển đẹp, nhiều quán ven biển"),
                    new Spot("Phố cổ + Chùa Cầu + nhà cổ", 150, "chieu", "vé tham quan phố cổ 120.000đ (5 điểm)"),
                    new Spot("Thả đèn hoa đăng sông Hoài", 60, "toi", "thuyền ~150.000–200.000đ/2 người"),
                    new Spot("Phố đèn lồng + chợ đêm Nguyễn Hoàng", 120, "toi", "đẹp nhất sau 18:30")),
            "Đẹp nhất chiều tối khi lên đèn lồng. Rằm âm lịch có đêm phố cổ tắt điện thắp đèn. Rất đông dịp lễ."),

        new CityGuide("nhatrang", "Nha Trang", List.of("nha trang", "nhatrang"),
            """
            • Taxi/Grab: nhiều, trung tâm nhỏ nên cuốc ngắn 30.000–70.000đ.
            • Thuê xe máy: 100.000–150.000đ/ngày, chạy đường ven biển Trần Phú rất thích.
            • Ra đảo: cano/tàu từ cảng Cầu Đá; tour 3–4 đảo 350.000–600.000đ/người.
            • Sân bay Cam Ranh cách trung tâm ~35km: xe bus sân bay 65.000đ hoặc taxi ~350.000đ.""",
            List.of(new Eat("Bún cá sứa", "Nguyên Loan (Ngô Gia Tự)", "35.000–50.000đ"),
                    new Eat("Nem nướng", "Đặng Văn Quyên, Vũ Thành An", "50.000–80.000đ"),
                    new Eat("Bánh căn", "đường Tháp Bà", "30.000–50.000đ"),
                    new Eat("Hải sản", "làng chài/quán dọc Trần Phú, Thái Thông", "200.000–500.000đ"),
                    new Eat("Xoài + kem bơ", "chợ Đầm, Kem bơ 76", "20.000–40.000đ")),
            List.of(new Spot("Tour 3 đảo (Hòn Mun lặn ngắm san hô…)", 300, "sang", "đặt trước, mang kem chống nắng"),
                    new Spot("VinWonders Hòn Tre", 300, "sang", "trọn buổi; cáp treo vượt biển"),
                    new Spot("Tháp Bà Ponagar", 90, "chieu", "vé 30.000đ, kiến trúc Chăm"),
                    new Spot("Tắm bùn khoáng Tháp Bà / I-Resort", 120, "chieu", "200.000–350.000đ/người"),
                    new Spot("Tắm biển Trần Phú + công viên bờ biển", 120, "any", "bãi trung tâm tiện nhất"),
                    new Spot("Chợ Đầm + ăn hải sản đêm", 120, "toi", "trả giá khi mua đồ khô")),
            "Mùa đẹp: 3–9. Tháng 10–12 hay mưa. Tour đảo nên đặt trước 1 ngày, say sóng nhớ mang thuốc."),

        new CityGuide("dalat", "Đà Lạt", List.of("da lat", "dalat"),
            """
            • Thuê xe máy: 100.000–130.000đ/ngày — phù hợp nhất, nhưng nhiều dốc + sương mù, tay lái phải vững.
            • Taxi: các điểm xa trung tâm 5–15km, nên gom cụm điểm theo hướng để tiết kiệm.
            • Xe đạp đôi quanh hồ Xuân Hương: 40.000–60.000đ/giờ.
            • Từ sân bay Liên Khương về trung tâm ~30km: xe trung chuyển 50.000đ hoặc taxi ~300.000đ.""",
            List.of(new Eat("Bánh căn", "Nhà Chung, Tăng Bạt Hổ", "30.000–50.000đ"),
                    new Eat("Lẩu gà lá é", "Tao Ngộ, 668", "150.000–250.000đ/nồi"),
                    new Eat("Bánh tráng nướng", "chợ đêm, Dì Đinh (Hoàng Diệu)", "15.000–35.000đ"),
                    new Eat("Sữa đậu nành nóng + bánh ngọt", "quanh chợ đêm", "10.000–25.000đ"),
                    new Eat("Cà phê view đồi", "Mê Linh, Túi Mơ To, Horizon", "40.000–70.000đ")),
            List.of(new Spot("Săn mây + cà phê view đồi", 90, "sang", "đi 5:30–6:30 sáng mới có mây"),
                    new Spot("Langbiang", 180, "sang", "xe jeep lên đỉnh ~100.000đ/người"),
                    new Spot("Thác Datanla + máng trượt", 120, "sang", "máng trượt 150.000–250.000đ"),
                    new Spot("Hồ Xuân Hương — đạp vịt/xe đạp đôi", 90, "chieu", "trung tâm, tiện ghé"),
                    new Spot("Ga Đà Lạt + chùa Linh Phước (Trại Mát)", 120, "chieu", "tàu cổ đi Trại Mát ~150.000đ khứ hồi"),
                    new Spot("Vườn dâu + đồi chè Cầu Đất", 150, "chieu", "Cầu Đất cách trung tâm ~20km"),
                    new Spot("Chợ đêm Đà Lạt", 120, "toi", "ăn vặt + mua đồ len, rất đông cuối tuần")),
            "Lạnh về đêm (12–18°C) — mang áo ấm cả mùa hè. Cuối tuần & lễ kẹt xe trung tâm, đặt phòng sớm."),

        new CityGuide("phuquoc", "Phú Quốc", List.of("phu quoc", "phuquoc"),
            """
            • Thuê xe máy: 120.000–150.000đ/ngày — đảo lớn (dài ~50km), đổ đầy xăng trước khi đi Bắc/Nam đảo.
            • Taxi/Grab: có nhưng ít xe giờ cao điểm, đi xa nên hẹn trước.
            • Cáp treo Hòn Thơm (dài nhất thế giới trên biển): nằm trong vé Sun World.
            • Cano tour 4 đảo Nam: 400.000–700.000đ/người, đặt tour ghép rất sẵn.""",
            List.of(new Eat("Gỏi cá trích", "quán Ra Khơi, Sông Xanh", "60.000–100.000đ"),
                    new Eat("Bún quậy", "Kiến Xây (thị trấn)", "40.000–70.000đ"),
                    new Eat("Ghẹ Hàm Ninh", "làng chài Hàm Ninh", "300.000–500.000đ/kg"),
                    new Eat("Hải sản chợ đêm", "chợ đêm Dinh Cậu/Phú Quốc", "200.000–600.000đ"),
                    new Eat("Sim rượu + tiêu + nước mắm", "mua làm quà tại vườn/nhà thùng", "tuỳ món")),
            List.of(new Spot("Cáp treo Hòn Thơm + công viên nước Aquatopia", 300, "sang", "trọn buổi, vé ~600.000–800.000đ"),
                    new Spot("Tour cano 4 đảo Nam — lặn ngắm san hô", 300, "sang", "đi ngày nắng, mang đồ bơi"),
                    new Spot("VinWonders + Safari (Bắc đảo)", 300, "sang", "trọn buổi cho gia đình có trẻ nhỏ"),
                    new Spot("Làng chài Hàm Ninh", 90, "chieu", "ăn ghẹ, ngắm biển"),
                    new Spot("Ngắm hoàng hôn Sunset Sanato / Bãi Trường", 120, "chieu", "đẹp nhất 17:00–18:30"),
                    new Spot("Grand World + show Tinh hoa Việt Nam", 150, "toi", "khu 'thành phố không ngủ' Bắc đảo"),
                    new Spot("Chợ đêm Dinh Cậu", 120, "toi", "hải sản + đồ nướng")),
            "Mùa đẹp: 11–4 (biển êm, ít mưa). 5–10 mưa nhiều. Bắc đảo & Nam đảo cách nhau xa — chia ngày theo khu."),

        new CityGuide("halong", "Hạ Long", List.of("ha long", "halong"),
            """
            • Du thuyền tham quan vịnh: tour trong ngày 4–6 tiếng (500.000–900.000đ) hoặc ngủ đêm trên vịnh.
            • Taxi + xe điện: di chuyển quanh khu Bãi Cháy, Hòn Gai.
            • Cáp treo Nữ Hoàng (Sun World): qua vịnh Cửa Lục, cabin 2 tầng.
            • Từ Hà Nội: cao tốc ~2,5 giờ, limousine 200.000–300.000đ/chiều.""",
            List.of(new Eat("Chả mực giã tay + bánh cuốn", "chả mực Bà Tài, chợ Hạ Long", "50.000–100.000đ"),
                    new Eat("Hải sản (sá sùng, bề bề, tu hài)", "bè nổi/quán khu Bãi Cháy, Hòn Gai", "300.000–700.000đ"),
                    new Eat("Bún bề bề", "chợ Hạ Long 1", "50.000–80.000đ"),
                    new Eat("Sữa chua trân châu", "phố Đoàn Thị Điểm", "20.000–35.000đ")),
            List.of(new Spot("Du thuyền vịnh Hạ Long + hang Sửng Sốt + đảo Titop", 300, "sang", "trọn buổi; vé thắng cảnh 290.000đ"),
                    new Spot("Chèo kayak/đò tay ở Luồn – Ba Hang", 90, "sang", "thường gộp trong tour thuyền"),
                    new Spot("Cáp treo Nữ Hoàng + vòng quay Mặt Trời", 150, "chieu", "vé Sun World ~350.000đ"),
                    new Spot("Bảo tàng Quảng Ninh", 90, "chieu", "toà nhà đen ven biển, vé 40.000đ"),
                    new Spot("Bãi tắm Bãi Cháy + cầu Bãi Cháy về đêm", 120, "toi", ""),
                    new Spot("Chợ đêm + phố ẩm thực Bãi Cháy", 120, "toi", "gần Sun World")),
            "Mùa đẹp: 4–10 (hè có thể đông). Mùa đông vịnh sương mù cũng đẹp kiểu khác. Say sóng nhớ mang thuốc."),

        new CityGuide("sapa", "Sa Pa", List.of("sa pa", "sapa"),
            """
            • Từ Hà Nội: xe giường nằm/limousine 5–6 giờ (250.000–450.000đ) hoặc tàu hoả đi Lào Cai + bus lên Sa Pa.
            • Trong thị trấn: đi bộ là chính; xe ôm/taxi đi bản 50.000–150.000đ/cuốc.
            • Cáp treo Fansipan: khứ hồi ~800.000đ; tàu hoả leo núi Mường Hoa từ trung tâm ra ga cáp.
            • Trekking bản làng nên thuê guide bản địa (~300.000–500.000đ/ngày).""",
            List.of(new Eat("Lẩu cá tầm/cá hồi", "khu chợ đêm, A Quỳnh", "300.000–500.000đ/nồi"),
                    new Eat("Thắng cố + rượu ngô", "chợ phiên, quán bản địa", "100.000–200.000đ"),
                    new Eat("Đồ nướng Sa Pa", "phố nướng gần nhà thờ đá", "10.000–50.000đ/xiên"),
                    new Eat("Cơm lam + gà bản", "các quán trong bản Cát Cát", "100.000–200.000đ"),
                    new Eat("Bánh hạt dẻ", "hàng rong trung tâm", "10.000–20.000đ")),
            List.of(new Spot("Cáp treo Fansipan — 'nóc nhà Đông Dương'", 240, "sang", "đi sớm tránh mây mù dày buổi chiều"),
                    new Spot("Trekking thung lũng Mường Hoa (Lao Chải – Tả Van)", 240, "sang", "đường ruộng bậc thang đẹp nhất 9–10"),
                    new Spot("Bản Cát Cát", 150, "chieu", "vé 150.000đ; gần trung tâm, dốc thoải"),
                    new Spot("Đèo Ô Quy Hồ — ngắm hoàng hôn", 120, "chieu", "cách trung tâm ~15km, lạnh, mang áo"),
                    new Spot("Nhà thờ đá + phố đi bộ", 90, "toi", "trái tim thị trấn"),
                    new Spot("Chợ đêm + văn nghệ dân tộc cuối tuần", 90, "toi", "thứ 7 có 'chợ tình'")),
            "Lạnh quanh năm về đêm, mùa đông có thể <5°C. Lúa chín 9–10 đẹp nhất; 12–2 có thể săn tuyết/mây."),

        new CityGuide("hue", "Huế", List.of("hue"),
            """
            • Thuê xe máy: 100.000–120.000đ/ngày — các lăng cách trung tâm 5–15km, chủ động nhất.
            • Xích lô dạo quanh Đại Nội + phố cổ Gia Hội: ~150.000đ/giờ.
            • Thuyền rồng sông Hương: nghe ca Huế buổi tối 100.000–150.000đ/người.
            • Taxi/Grab sẵn; đi cụm lăng nên gom tuyến Tự Đức → Khải Định → Minh Mạng.""",
            List.of(new Eat("Bún bò Huế", "Bà Tuyết, Mụ Rơi", "35.000–60.000đ"),
                    new Eat("Bánh bèo – nậm – lọc", "Bà Đỏ, Hàng Me", "30.000–60.000đ"),
                    new Eat("Cơm hến + bún hến", "cồn Hến, Hoa Đông", "20.000–40.000đ"),
                    new Eat("Chè Huế (chè bột lọc heo quay!)", "chè Hẻm, chè Mợ Tôn Đích", "15.000–30.000đ"),
                    new Eat("Bánh khoái", "Lạc Thiện (cửa Thượng Tứ)", "40.000–70.000đ")),
            List.of(new Spot("Đại Nội (Hoàng thành) + Bảo tàng Cổ vật", 180, "sang", "vé 200.000đ; đi sớm nắng dịu"),
                    new Spot("Chùa Thiên Mụ", 75, "chieu", "ven sông Hương, miễn phí"),
                    new Spot("Lăng Tự Đức", 120, "chieu", "vé 150.000đ, thơ mộng nhất"),
                    new Spot("Lăng Khải Định", 90, "chieu", "vé 150.000đ, khảm sành lộng lẫy"),
                    new Spot("Đồi Vọng Cảnh — ngắm sông Hương", 60, "chieu", "hoàng hôn đẹp"),
                    new Spot("Chợ Đông Ba", 90, "any", "mua mè xửng, nón lá"),
                    new Spot("Thuyền rồng sông Hương + ca Huế", 90, "toi", "lên thuyền bến Toà Khâm ~19:00")),
            "Mưa nhiều 10–12. Vé gộp Đại Nội + lăng rẻ hơn mua lẻ. Trang phục lịch sự khi vào lăng/chùa."),

        new CityGuide("cantho", "Cần Thơ", List.of("can tho", "cantho"),
            """
            • Ghe/tàu đi chợ nổi: thuê ghe nhỏ 150.000–250.000đ/giờ tại bến Ninh Kiều, đi 5:00–6:00 sáng.
            • Taxi/Grab: sẵn trong trung tâm.
            • Thuê xe máy: 100.000–130.000đ/ngày để đi nhà cổ, vườn trái cây.
            • Đi bộ: khu bến Ninh Kiều — cầu đi bộ — chợ đêm rất gần nhau.""",
            List.of(new Eat("Lẩu mắm", "Dạ Lý (3/2)", "150.000–300.000đ/nồi"),
                    new Eat("Bánh cống", "Cô Út (Đề Thám)", "15.000–30.000đ/cái"),
                    new Eat("Nem nướng Cái Răng", "Thanh Vân", "50.000–80.000đ"),
                    new Eat("Trái cây miệt vườn", "vườn Mỹ Khánh, chợ nổi", "tuỳ mùa"),
                    new Eat("Bún riêu + ốc chợ đêm", "chợ đêm Tây Đô/Ninh Kiều", "30.000–80.000đ")),
            List.of(new Spot("Chợ nổi Cái Răng + ăn sáng trên ghe", 180, "sang", "xuất phát 5:00–5:30, tan sớm ~8:00"),
                    new Spot("Vườn trái cây Mỹ Khánh", 150, "chieu", "hái trái cây theo mùa, vé ~30.000đ"),
                    new Spot("Nhà cổ Bình Thuỷ", 75, "chieu", "vé 15.000đ, bối cảnh phim 'Người tình'"),
                    new Spot("Thiền viện Trúc Lâm Phương Nam", 90, "chieu", "miễn phí, trang phục kín đáo"),
                    new Spot("Bến Ninh Kiều + cầu đi bộ", 90, "toi", "đèn đẹp sau 18:30"),
                    new Spot("Chợ đêm Tây Đô", 90, "toi", "ăn vặt miền Tây")),
            "Chợ nổi PHẢI đi sáng sớm — ngủ sớm đêm trước. Mùa trái cây hè (5–8) đi vườn là đã nhất."),

        new CityGuide("vungtau", "Vũng Tàu", List.of("vung tau", "vungtau"),
            """
            • Từ TP.HCM: bus/limousine 2–2,5 giờ (150.000–200.000đ) hoặc phà cao tốc.
            • Thuê xe máy: 100.000–150.000đ/ngày, chạy đường ven biển Trần Phú – Hạ Long rất đẹp.
            • Taxi/xe điện: quanh Bãi Trước, Bãi Sau.
            • Leo tượng Chúa & hải đăng: đi bộ — mang giày thể thao, nước uống.""",
            List.of(new Eat("Bánh khọt", "Gốc Vú Sữa (Nguyễn Trường Tộ)", "50.000–80.000đ"),
                    new Eat("Lẩu cá đuối", "Hoàng Minh, quán 7 Lượng", "150.000–300.000đ"),
                    new Eat("Hải sản", "làng chài Bến Đá, quán dọc Bãi Sau", "200.000–500.000đ"),
                    new Eat("Bánh bông lan trứng muối", "Gốc Cột Điện, mua làm quà", "10.000–30.000đ"),
                    new Eat("Cà phê view biển", "đồi Con Heo, OCEAN view", "40.000–70.000đ")),
            List.of(new Spot("Tượng Chúa Kitô Vua — leo 800 bậc", 120, "sang", "đi sớm cho mát; lên tay tượng ngắm toàn cảnh"),
                    new Spot("Ngọn hải đăng Vũng Tàu", 75, "chieu", "một trong những hải đăng cổ nhất VN"),
                    new Spot("Tắm biển Bãi Sau", 150, "any", "sóng vừa, dịch vụ đầy đủ"),
                    new Spot("Mũi Nghinh Phong + cổng trời", 60, "chieu", "chụp ảnh đẹp"),
                    new Spot("Hồ Mây Park (cáp treo Núi Lớn)", 180, "sang", "khu vui chơi trên núi, vé ~300.000đ"),
                    new Spot("Bãi Trước — dạo biển, ăn hải sản tối", 120, "toi", "hoàng hôn đẹp")),
            "Cuối tuần cực đông khách từ TP.HCM — đi ngày thường vắng hơn, giá phòng mềm hơn.")
    );
}
