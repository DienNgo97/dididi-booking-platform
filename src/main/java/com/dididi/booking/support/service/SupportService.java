package com.dididi.booking.support.service;

import com.dididi.booking.support.domain.SupportMessage;
import com.dididi.booking.support.domain.SupportRole;
import com.dididi.booking.support.dto.ConversationSummaryDto;
import com.dididi.booking.support.dto.SupportAnswer;
import com.dididi.booking.support.dto.SupportMessageDto;
import com.dididi.booking.support.dto.SupportStatsDto;
import com.dididi.booking.support.repository.SupportMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * "Bộ não" trợ lý CSKH (hybrid):
 *   1) Tri thức nội bộ (KB) — luật khớp từ khoá, trả lời chuẩn chính sách, chạy offline.
 *   2) Nếu KB không khớp và đã cấu hình LLM (api-key) -> hỏi LLM.
 *   3) Vẫn không có -> trả lời lịch sự + gợi ý chuyển tổng đài viên (escalate).
 *
 * Đồng thời LƯU mọi tin nhắn vào DB để thống kê & huấn luyện chatbot sau này.
 */
@Service
public class SupportService {

    private static final Logger log = LoggerFactory.getLogger(SupportService.class);
    private static final int MAX_LEN = 2000;

    private final SupportAiClient ai;
    private final SupportMessageRepository repo;
    private final MessageSource messages;

    public SupportService(SupportAiClient ai, SupportMessageRepository repo, MessageSource messages) {
        this.ai = ai;
        this.repo = repo;
        this.messages = messages;
    }

    /**
     * 1 mục tri thức: hỏi chứa BẤT KỲ từ khoá nào -> trả lời tương ứng (key i18n kb.*).
     * Từ khoá tiếng Việt viết KHÔNG dấu; có thêm từ khoá tiếng Anh/Trung để khớp đa ngôn ngữ.
     */
    private record Faq(List<String> keys, String answerKey, boolean escalate) {}

    private static final List<Faq> KB = List.of(
            new Faq(List.of("huy don", "huy phong", "huy ve", "huy dat", "huy booking", "lam sao huy", "muon huy", "co huy duoc",
                    "cancel", "how to cancel", "cancel booking", "cancel my",
                    "取消", "退订", "怎么取消", "能取消", "如何取消"),
                    "kb.cancel", false),

            new Faq(List.of("48", "bao lau", "han huy", "tre han", "qua han", "con bao nhieu gio",
                    "how long", "deadline", "free cancel",
                    "多久", "期限", "多长时间", "免费取消"),
                    "kb.deadline", false),

            new Faq(List.of("hoan tien", "lay lai tien", "hoan lai", "bao gio co tien",
                    "refund", "money back", "get my money",
                    "退款", "退钱", "什么时候到账", "钱什么时候"),
                    "kb.refund", false),

            new Faq(List.of("doi ngay", "doi lich", "sua don", "thay doi ngay", "doi gio", "chinh sua", "doi phong",
                    "change date", "reschedule", "modify", "edit booking", "change room",
                    "改期", "改日期", "修改订单", "换房", "改时间"),
                    "kb.change", false),

            new Faq(List.of("thanh toan", "tra tien", "the tin dung", "the atm", "the ngan hang", "chuyen khoan", "cong thanh toan",
                    "payment", "pay", "vnpay", "how to pay", "card",
                    "支付", "付款", "怎么付", "银行卡", "怎么支付", "刷卡"),
                    "kb.payment", false),

            new Faq(List.of("diem", "tich diem", "hang thanh vien", "hang bac", "vang", "kim cuong", "doi diem",
                    "point", "loyalty", "tier", "member", "redeem",
                    "积分", "会员", "等级", "兑换", "升级"),
                    "kb.points", false),

            new Faq(List.of("tra cuu", "don cua toi", "xem don", "ma don", "tim don", "lich su dat",
                    "my booking", "my order", "find booking", "booking code", "order history",
                    "我的订单", "查订单", "订单号", "订单历史", "查询订单"),
                    "kb.lookup", false),

            new Faq(List.of("hanh ly", "suat an", "bua an", "ky gui", "xach tay", "an tren may bay",
                    "baggage", "luggage", "meal", "carry on", "checked bag",
                    "行李", "餐食", "托运", "随身", "飞机餐"),
                    "kb.baggage", false),

            new Faq(List.of("gio nhan phong", "check in", "checkin", "gio tra phong", "check out", "checkout", "may gio",
                    "what time", "check-in", "check-out",
                    "入住", "退房", "几点", "入住时间", "退房时间"),
                    "kb.checkin", false),

            new Faq(List.of("hotline", "tong dai", "lien he", "so dien thoai", "gap nguoi", "nhan vien", "goi dien", "gap tong dai",
                    "agent", "contact", "phone", "talk to human", "call center", "human",
                    "热线", "人工", "客服", "电话", "联系", "转人工"),
                    "kb.hotline", true),

            new Faq(List.of("xin chao", "chao ban", "alo", "co ai khong", "ban oi",
                    "hello", "hi ", "hey ", "anyone",
                    "你好", "您好", "在吗", "有人"),
                    "kb.greeting", false),

            new Faq(List.of("cam on", "ok ban", "tot qua",
                    "thank", "thanks",
                    "谢谢", "感谢", "多谢"),
                    "kb.thanks", false)
    );

    public boolean llmEnabled() {
        return ai.isEnabled();
    }

    // ===== Hỏi-đáp (có lưu DB) =====

    /** Trả lời 1 câu hỏi và LƯU cả câu hỏi (USER) lẫn câu trả lời (BOT) vào DB. */
    public SupportAnswer answer(String question, String conversationId, Long userId, String bookingCode) {
        Locale loc = LocaleContextHolder.getLocale();
        String cid = (conversationId == null || conversationId.isBlank())
                ? UUID.randomUUID().toString() : conversationId.trim();
        save(cid, SupportRole.USER, question, null, false, userId, bookingCode);
        SupportAnswer ans = compute(question, loc);
        save(cid, SupportRole.BOT, ans.answer(), ans.source(), ans.escalate(), userId, bookingCode);
        return ans;
    }

    /** Tính câu trả lời (KB -> LLM -> none) theo ngôn ngữ người dùng, KHÔNG đụng DB. */
    private SupportAnswer compute(String question, Locale loc) {
        String norm = normalize(question);
        if (norm.isBlank()) {
            return SupportAnswer.kb(msg("kb.empty", loc));
        }
        for (Faq f : KB) {
            for (String k : f.keys()) {
                if (norm.contains(k)) {
                    String a = msg(f.answerKey(), loc);
                    return f.escalate() ? SupportAnswer.kbEscalate(a) : SupportAnswer.kb(a);
                }
            }
        }
        Optional<String> llm = ai.ask(question, loc);
        if (llm.isPresent()) {
            return SupportAnswer.llm(llm.get());
        }
        return SupportAnswer.none(msg("kb.none", loc));
    }

    /** Lấy 1 câu trả lời KB theo locale; key thiếu -> trả về chính key (không làm vỡ chat). */
    private String msg(String code, Locale loc) {
        return messages.getMessage(code, null, code, loc);
    }

    /** Ghi 1 tin nhắn do client gửi (phần tổng đài mô phỏng, sự kiện escalate...). */
    public void logMessage(String conversationId, SupportRole role, String content,
                           Long userId, String bookingCode, boolean escalated) {
        String cid = (conversationId == null || conversationId.isBlank())
                ? UUID.randomUUID().toString() : conversationId.trim();
        save(cid, role, content, null, escalated, userId, bookingCode);
    }

    /** Lưu 1 dòng; mọi lỗi DB được nuốt để KHÔNG làm hỏng luồng chat. */
    private void save(String cid, SupportRole role, String content, String source,
                      boolean escalated, Long userId, String bookingCode) {
        try {
            SupportMessage m = new SupportMessage();
            m.setConversationId(cid);
            m.setRole(role);
            m.setContent(clamp(content));
            m.setSource(source);
            m.setEscalated(escalated);
            m.setUserId(userId);
            m.setBookingCode(bookingCode);
            repo.save(m);
        } catch (Exception ex) {
            log.warn("Không lưu được tin nhắn hỗ trợ: {}", ex.toString());
        }
    }

    private static String clamp(String s) {
        if (s == null) return "";
        return s.length() > MAX_LEN ? s.substring(0, MAX_LEN) : s;
    }

    // ===== Thống kê & xem log (admin) =====

    public SupportStatsDto stats() {
        long total = repo.count();
        long questions = repo.countByRole(SupportRole.USER);
        long convs = repo.countConversations();
        long escConvs = repo.countEscalatedConversations();
        double rate = convs == 0 ? 0.0 : Math.round(escConvs * 1000.0 / convs) / 10.0;
        long kb = repo.countBySource("kb");
        long llm = repo.countBySource("llm");
        long none = repo.countBySource("none");
        long agents = repo.countByRole(SupportRole.AGENT);
        return new SupportStatsDto(total, questions, convs, escConvs, rate, kb, llm, none, agents);
    }

    public List<ConversationSummaryDto> conversations() {
        List<ConversationSummaryDto> out = new ArrayList<>();
        for (Object[] r : repo.conversationSummaries()) {
            out.add(new ConversationSummaryDto(
                    (String) r[0],
                    ((Number) r[1]).longValue(),
                    toInstant(r[2]),
                    toInstant(r[3]),
                    ((Number) r[4]).intValue() > 0,
                    r[5] == null ? null : ((Number) r[5]).longValue()));
        }
        return out;
    }

    public List<SupportMessageDto> messages(String conversationId) {
        return repo.findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream().map(SupportMessageDto::from).toList();
    }

    private static Instant toInstant(Object o) {
        if (o instanceof Instant i) return i;
        if (o instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (o instanceof java.util.Date d) return d.toInstant();
        return null;
    }

    /** Chuẩn hoá: bỏ dấu tiếng Việt + thường hoá, để khớp từ khoá không phụ thuộc dấu. */
    static String normalize(String s) {
        if (s == null) return "";
        String lower = s.toLowerCase().trim();
        String noMark = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return noMark.replace('đ', 'd');
    }
}
