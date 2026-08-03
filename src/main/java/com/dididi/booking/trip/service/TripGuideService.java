package com.dididi.booking.trip.service;

import com.dididi.booking.support.service.SupportAiClient;
import com.dididi.booking.trip.dto.TripGuideAnswer;
import com.dididi.booking.trip.service.TripGuideKb.CityGuide;
import com.dididi.booking.trip.service.TripGuideKb.Eat;
import com.dididi.booking.trip.service.TripGuideKb.Spot;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI HƯỚNG DẪN VIÊN DU LỊCH (Trip Guide) — kiến trúc HYBRID giống chatbot CSKH:
 *
 *  1) Có API key LLM (app.support.llm.api-key): câu hỏi được gửi cho LLM với system prompt
 *     "hướng dẫn viên du lịch" + TRI THỨC NỘI BỘ của thành phố nhận diện được làm căn cứ
 *     (grounding — hạn chế bịa giá/số liệu). LLM lỗi/hết hạn mức -> tự rơi xuống nhánh 2.
 *  2) KHÔNG có key (mặc định): trả lời hoàn toàn từ {@link TripGuideKb} — nhận diện
 *     thành phố + ý định (đi lại / ăn uống / vui chơi / lịch trình N ngày) rồi soạn câu trả lời,
 *     trong đó lịch trình được XẾP THEO MỐC GIỜ từ thời lượng dự kiến của từng điểm.
 *
 * Trả lời dạng văn bản thuần (không markdown) — client hiển thị với white-space:pre-line.
 */
@Service
public class TripGuideService {

    private static final Pattern DAYS = Pattern.compile("(\\d{1,2})\\s*(?:ngay|day)");

    // Ngân sách phút cho từng buổi khi xếp lịch (đã trừ hao di chuyển giữa các điểm ~30').
    private static final int MORNING_START = 8 * 60;        // 08:00
    private static final int MORNING_BUDGET = 210;          // tới ~11:30
    private static final int AFTERNOON_START = 13 * 60 + 30;// 13:30
    private static final int AFTERNOON_BUDGET = 210;        // tới ~17:00
    private static final int EVENING_START = 19 * 60 + 30;  // 19:30 (sau bữa tối 18:00)
    private static final int EVENING_BUDGET = 150;
    private static final int TRAVEL_GAP = 30;               // phút di chuyển giữa 2 điểm

    private final SupportAiClient aiClient;

    public TripGuideService(SupportAiClient aiClient) {
        this.aiClient = aiClient;
    }

    public TripGuideAnswer answer(String q, Locale locale) {
        String question = q == null ? "" : q.trim();
        if (question.isBlank()) {
            return help();
        }
        String norm = normalize(question);
        CityGuide city = detectCity(norm);
        Set<String> intents = detectIntents(norm);
        int days = detectDays(norm);
        if (days > 0) {
            intents.add("lichtrinh");
        }

        // ----- Nhánh 1: LLM (nếu cấu hình api-key) — KB làm căn cứ -----
        if (aiClient.isEnabled()) {
            // 4000 token: model thinking (Gemini 3) tính cả token suy nghĩ vào max_tokens,
            // lịch trình dài + suy nghĩ cần trần cao mới không bị cụt.
            Optional<String> llm = aiClient.ask(llmSystemPrompt(city), question, locale, 4000);
            if (llm.isPresent()) {
                return new TripGuideAnswer(llm.get(), "llm", suggestsFor(city, intents));
            }
        }

        // ----- Nhánh 2: KB nội bộ -----
        if (city == null) {
            return help();
        }
        String answer;
        if (intents.contains("lichtrinh")) {
            answer = itinerary(city, days <= 0 ? 2 : Math.min(days, 4));
        } else if (intents.isEmpty()) {
            answer = overview(city);
        } else {
            StringBuilder sb = new StringBuilder();
            if (intents.contains("dichuyen")) sb.append(transportSection(city)).append("\n\n");
            if (intents.contains("anuong"))   sb.append(foodSection(city)).append("\n\n");
            if (intents.contains("vuichoi"))  sb.append(funSection(city)).append("\n\n");
            answer = sb.toString().trim();
        }
        return new TripGuideAnswer(answer, "kb", suggestsFor(city, intents));
    }

    // ================= Nhận diện =================

    /** Thành phố: so khớp alias KHÔNG DẤU theo ranh giới từ; nhiều TP trong câu -> lấy TP đứng SAU (điểm đến). */
    private CityGuide detectCity(String norm) {
        CityGuide best = null;
        int bestPos = -1;
        for (CityGuide c : TripGuideKb.CITIES) {
            for (String alias : c.aliases()) {
                Matcher m = Pattern.compile("(?<![a-z0-9])" + Pattern.quote(alias) + "(?![a-z0-9])").matcher(norm);
                while (m.find()) {
                    if (m.start() > bestPos) {
                        bestPos = m.start();
                        best = c;
                    }
                }
            }
        }
        return best;
    }

    private Set<String> detectIntents(String norm) {
        Set<String> out = new LinkedHashSet<>();
        if (containsAny(norm, "di chuyen", "phuong tien", "di lai", "xe bus", "xe buyt", "tau dien", "metro",
                "taxi", "grab", "thue xe", "xe may", "xe om", "cach di", "den bang gi", "di bang gi", "xe dap",
                "transport", "get around", "how to get", "bus", "train")) {
            out.add("dichuyen");
        }
        if (containsAny(norm, "an uong", "an gi", "mon ngon", "mon an", "dac san", "quan an", "am thuc",
                "nha hang", "an o dau", "an sang", "an trua", "an toi", "ca phe", "cafe", "quan ngon", "hai san",
                "eat", "food", "restaurant")) {
            out.add("anuong");
        }
        if (containsAny(norm, "vui choi", "giai tri", "tham quan", "check in", "check-in", "dia diem",
                "canh dep", "kham pha", "choi gi", "di dau", "cho choi", "danh lam", "thang canh", "diem den",
                "things to do", "attraction", "sightsee", "visit")) {
            out.add("vuichoi");
        }
        if (containsAny(norm, "lich trinh", "ke hoach", "lo trinh", "itinerary", "len lich", "xay dung lich",
                "plan ", "schedule")) {
            out.add("lichtrinh");
        }
        return out;
    }

    private int detectDays(String norm) {
        Matcher m = DAYS.matcher(norm);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) { }
        }
        return 0;
    }

    // ================= Soạn trả lời từ KB =================

    private TripGuideAnswer help() {
        String cities = String.join(", ", TripGuideKb.CITIES.stream().map(CityGuide::name).toList());
        String answer = """
                Chào bạn! Mình là AI hướng dẫn viên du lịch của Dididi. Mình có thể giúp:
                • Lịch trình tham quan theo mốc giờ (vd: "lịch trình 3 ngày ở Đà Nẵng")
                • Phương tiện di chuyển tại điểm đến (bus, tàu điện, taxi, thuê xe máy…)
                • Ăn uống — đặc sản, quán ngon, khoảng giá
                • Vui chơi – giải trí + thời gian khám phá dự kiến từng nơi
                • …và mọi thắc mắc khác cho chuyến đi: chi phí, thời tiết, mùa đẹp, đồ mang theo, văn hoá, quà mua về…
                Mình rành nhất về: %s — nhưng cứ hỏi bất kỳ điểm đến nào bạn muốn nhé.
                Bạn muốn đi đâu?""".formatted(cities);
        return new TripGuideAnswer(answer, "kb",
                List.of("Lịch trình 3 ngày ở Đà Nẵng", "Ăn gì ở Huế?", "Đi lại ở Hà Nội thế nào?"));
    }

    private String overview(CityGuide c) {
        return c.name() + " — mình giúp được gì cho chuyến đi của bạn?\n"
                + "• Lịch trình mẫu theo giờ: hỏi \"lịch trình 2-3 ngày ở " + c.name() + "\"\n"
                + "• Đi lại: hỏi \"đi lại ở " + c.name() + " thế nào?\"\n"
                + "• Ăn uống: hỏi \"ăn gì ở " + c.name() + "?\"\n"
                + "• Vui chơi: hỏi \"chơi gì ở " + c.name() + "?\"\n"
                + "Mẹo: " + c.tips();
    }

    private String transportSection(CityGuide c) {
        return "PHƯƠNG TIỆN DI CHUYỂN Ở " + c.name().toUpperCase(Locale.ROOT) + "\n" + c.transport();
    }

    private String foodSection(CityGuide c) {
        StringBuilder sb = new StringBuilder("ĂN GÌ Ở " + c.name().toUpperCase(Locale.ROOT) + "?\n");
        for (Eat e : c.eats()) {
            sb.append("• ").append(e.dish()).append(" — ").append(e.where())
              .append(" (").append(e.price()).append(")\n");
        }
        sb.append("Mẹo: ").append(c.tips());
        return sb.toString();
    }

    private String funSection(CityGuide c) {
        StringBuilder sb = new StringBuilder("VUI CHƠI – THAM QUAN Ở " + c.name().toUpperCase(Locale.ROOT)
                + " (kèm thời gian khám phá dự kiến)\n");
        for (Spot s : c.spots()) {
            sb.append("• ").append(s.name()).append(" — khoảng ").append(fmtDuration(s.minutes()))
              .append(", hợp buổi ").append(slotLabel(s.slot()));
            if (!s.note().isBlank()) sb.append(". ").append(s.note());
            sb.append("\n");
        }
        sb.append("Muốn mình xếp thành lịch trình theo giờ? Hỏi \"lịch trình 2 ngày ở ").append(c.name()).append("\".");
        return sb.toString();
    }

    /**
     * Xếp LỊCH TRÌNH N NGÀY theo mốc giờ từ thời lượng dự kiến của từng điểm trong KB.
     * Giờ ăn trưa/tối được tính ĐỘNG theo giờ kết thúc thực của buổi (điểm "trọn buổi" như
     * Bà Nà 5 tiếng có thể kéo qua trưa -> bữa trưa tự lùi theo).
     */
    private String itinerary(CityGuide c, int days) {
        Deque<Spot> morning = pool(c, "sang");
        Deque<Spot> afternoon = pool(c, "chieu");
        Deque<Spot> evening = pool(c, "toi");
        Deque<Spot> any = pool(c, "any");
        List<Eat> eats = c.eats();
        int eatIdx = 0;

        StringBuilder sb = new StringBuilder("LỊCH TRÌNH " + days + " NGÀY Ở " + c.name().toUpperCase(Locale.ROOT) + "\n");
        for (int d = 1; d <= days; d++) {
            sb.append("\nNGÀY ").append(d).append(":\n");
            int mEnd = appendSlot(sb, MORNING_START, MORNING_BUDGET, 2, morning, any);
            int lunchAt = Math.max(11 * 60 + 45, mEnd + 15);
            Eat lunch = eats.get(eatIdx++ % eats.size());
            sb.append("• ").append(hhmm(lunchAt)).append("–").append(hhmm(lunchAt + 75))
              .append(" · Ăn trưa: ").append(lunch.dish()).append(" — ")
              .append(lunch.where()).append(" (").append(lunch.price()).append(")\n");
            int aStart = Math.max(AFTERNOON_START, lunchAt + 75 + TRAVEL_GAP);
            int aEnd = appendSlot(sb, aStart, AFTERNOON_BUDGET, 2, afternoon, any);
            int dinnerAt = Math.max(18 * 60, aEnd + TRAVEL_GAP);
            Eat dinner = eats.get(eatIdx++ % eats.size());
            sb.append("• ").append(hhmm(dinnerAt)).append("–").append(hhmm(dinnerAt + 75))
              .append(" · Ăn tối: ").append(dinner.dish()).append(" — ")
              .append(dinner.where()).append(" (").append(dinner.price()).append(")\n");
            appendSlot(sb, Math.max(EVENING_START, dinnerAt + 75 + 15), EVENING_BUDGET, 2, evening, null);
        }
        sb.append("\nLưu ý: thời lượng chỉ là DỰ KIẾN, đã cộng ~30 phút di chuyển giữa các điểm.\n");
        sb.append("Hỏi thêm: \"đi lại ở ").append(c.name()).append("\" để xem phương tiện, \"ăn gì ở ")
          .append(c.name()).append("\" để xem đủ món.");
        return sb.toString();
    }

    /**
     * Nhét tối đa maxSpots điểm vào 1 buổi theo ngân sách phút; hết pool chính thì lấy pool 'any'.
     * Điểm ĐẦU BUỔI được phép vượt ngân sách (các điểm "trọn buổi" 4-5 tiếng: Bà Nà, tour đảo,
     * Fansipan… vẫn được xếp — nếu không sẽ chẳng bao giờ vào lịch).
     * @return mốc phút KẾT THÚC điểm cuối của buổi (để tính giờ bữa ăn kế tiếp).
     */
    private int appendSlot(StringBuilder sb, int startMin, int budget, int maxSpots,
                           Deque<Spot> primary, Deque<Spot> fallback) {
        int clock = startMin;
        int taken = 0;
        while (taken < maxSpots) {
            int remain = startMin + budget - clock;
            Spot s;
            if (taken == 0) {
                s = primary.pollFirst();                       // điểm đầu buổi: cho phép "trọn buổi"
                if (s == null && fallback != null) s = pollFitting(fallback, remain);
            } else {
                s = pollFitting(primary, remain);
                if (s == null && fallback != null) s = pollFitting(fallback, remain);
            }
            if (s == null) break;
            int end = clock + s.minutes();
            sb.append("• ").append(hhmm(clock)).append("–").append(hhmm(end)).append(" · ")
              .append(s.name()).append(" (").append(fmtDuration(s.minutes())).append(")");
            if (!s.note().isBlank()) sb.append(" — ").append(s.note());
            sb.append("\n");
            clock = end + TRAVEL_GAP;
            taken++;
        }
        if (taken == 0) {
            sb.append("• ").append(hhmm(startMin)).append("~ · Tự do khám phá, nghỉ ngơi hoặc cà phê ngắm phố\n");
            return startMin;
        }
        return clock - TRAVEL_GAP;
    }

    /** Lấy điểm ĐẦU TIÊN trong pool vừa với số phút còn lại (giữ thứ tự ưu tiên của KB). */
    private Spot pollFitting(Deque<Spot> pool, int remain) {
        if (pool == null) return null;
        for (Spot s : pool) {
            if (s.minutes() <= remain) {
                pool.remove(s);
                return s;
            }
        }
        return null;
    }

    private Deque<Spot> pool(CityGuide c, String slot) {
        Deque<Spot> d = new ArrayDeque<>();
        for (Spot s : c.spots()) {
            if (slot.equals(s.slot())) d.add(s);
        }
        return d;
    }

    // ================= LLM =================

    private String llmSystemPrompt(CityGuide city) {
        StringBuilder sb = new StringBuilder("""
                Bạn là "AI Hướng dẫn viên du lịch" của Dididi — nền tảng đặt khách sạn & vé máy bay tại Việt Nam.
                Nhiệm vụ: tư vấn MỌI KHÍA CẠNH của một chuyến đi. Các thế mạnh tiêu biểu (ví dụ, KHÔNG phải giới hạn):
                lịch trình tham quan theo mốc giờ HH:mm kèm thời lượng dự kiến từng điểm; phương tiện di chuyển
                tại điểm đến; chỗ ăn uống; khu vui chơi – giải trí.
                Ngoài ra hãy chủ động tư vấn bất cứ điều gì hữu ích cho chuyến đi mà bạn có thông tin, ví dụ:
                ngân sách & chi phí dự kiến, thời điểm/mùa đẹp nhất, thời tiết, đồ cần mang theo, văn hoá & lưu ý
                ứng xử địa phương, an toàn & sức khoẻ, lễ hội – sự kiện, quà & đặc sản mua về, mẹo chụp ảnh,
                gợi ý cho gia đình có trẻ nhỏ / người lớn tuổi / cặp đôi, tối ưu chi phí…
                Trình bày bằng gạch đầu dòng "•", KHÔNG dùng markdown (không **, không #). Ngắn gọn, đúng trọng tâm
                câu hỏi (thường dưới ~350 từ; chỉ dài hơn khi người dùng hỏi lịch trình nhiều ngày).
                Chỉ từ chối khi câu hỏi HOÀN TOÀN không liên quan du lịch (vd: code, chính trị) — từ chối khéo
                và gợi ý quay lại chủ đề chuyến đi.
                Nếu có DỮ LIỆU THAM KHẢO bên dưới, ưu tiên dùng đúng thông tin/giá trong đó, tuyệt đối không bịa giá;
                phần dữ liệu không đề cập thì dùng hiểu biết của bạn.
                """);
        if (city != null) {
            sb.append("\n===== DỮ LIỆU THAM KHẢO: ").append(city.name()).append(" =====\n");
            sb.append("[Di chuyển]\n").append(city.transport()).append("\n[Ăn uống]\n");
            for (Eat e : city.eats()) {
                sb.append("- ").append(e.dish()).append(" | ").append(e.where()).append(" | ").append(e.price()).append("\n");
            }
            sb.append("[Tham quan (phút | buổi hợp)]\n");
            for (Spot s : city.spots()) {
                sb.append("- ").append(s.name()).append(" | ").append(s.minutes()).append("' | ")
                  .append(s.slot()).append(" | ").append(s.note()).append("\n");
            }
            sb.append("[Mẹo] ").append(city.tips()).append("\n");
        } else {
            sb.append("\nCác thành phố có dữ liệu chi tiết: ")
              .append(String.join(", ", TripGuideKb.CITIES.stream().map(CityGuide::name).toList()))
              .append(".\n");
        }
        return sb.toString();
    }

    // ================= Gợi ý câu hỏi tiếp theo =================

    private List<String> suggestsFor(CityGuide city, Set<String> intents) {
        if (city == null) {
            return List.of("Lịch trình 3 ngày ở Đà Nẵng", "Ăn gì ở Huế?", "Đi lại ở Hà Nội thế nào?");
        }
        List<String> out = new ArrayList<>(3);
        if (!intents.contains("lichtrinh")) out.add("Lịch trình 2 ngày ở " + city.name());
        if (!intents.contains("dichuyen"))  out.add("Đi lại ở " + city.name() + " thế nào?");
        if (!intents.contains("anuong"))    out.add("Ăn gì ở " + city.name() + "?");
        if (!intents.contains("vuichoi") && out.size() < 3) out.add("Chơi gì ở " + city.name() + "?");
        return out.size() > 3 ? out.subList(0, 3) : out;
    }

    // ================= Tiện ích =================

    private static boolean containsAny(String norm, String... keys) {
        for (String k : keys) {
            if (norm.contains(k)) return true;
        }
        return false;
    }

    private static String hhmm(int minutes) {
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }

    private static String fmtDuration(int minutes) {
        if (minutes < 60) return minutes + " phút";
        int h = minutes / 60, m = minutes % 60;
        return m == 0 ? h + " giờ" : h + " giờ " + m + " phút";
    }

    private static String slotLabel(String slot) {
        return switch (slot) {
            case "sang" -> "sáng";
            case "chieu" -> "chiều";
            case "toi" -> "tối";
            default -> "nào cũng được";
        };
    }

    /** Bỏ dấu tiếng Việt + lowercase (giống TripPlannerService). */
    private static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
    }
}
