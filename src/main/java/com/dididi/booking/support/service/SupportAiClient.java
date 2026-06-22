package com.dididi.booking.support.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Gọi LLM bên ngoài (tuỳ chọn) cho nhánh "câu lạ" của trợ lý CSKH.
 *
 * An toàn cho đồ án: nếu KHÔNG cấu hình api-key (mặc định rỗng) thì {@link #ask} luôn trả Optional.empty()
 * và hệ thống tự rơi về tri thức nội bộ / chuyển tổng đài. Mọi lỗi mạng/parse đều được nuốt -> empty,
 * nên thiếu key hay rớt mạng cũng KHÔNG làm hỏng luồng chat.
 *
 * Hỗ trợ 2 nhà cung cấp:
 *  - openai   : POST https://api.openai.com/v1/chat/completions
 *  - anthropic: POST https://api.anthropic.com/v1/messages
 * Cấu hình qua app.support.llm.* (xem application.yml).
 */
@Component
public class SupportAiClient {

    private static final Logger log = LoggerFactory.getLogger(SupportAiClient.class);

    private static final String SYSTEM_PROMPT = """
            Bạn là trợ lý chăm sóc khách hàng của Dididi — nền tảng đặt khách sạn và vé máy bay.
            Trả lời NGẮN GỌN (tối đa 4-5 câu), lịch sự, thân thiện.
            Chính sách quan trọng phải tuân thủ:
            - Khách chỉ TỰ HUỶ đơn (khách sạn hoặc vé máy bay) khi còn HƠN 48 GIỜ trước giờ nhận phòng / giờ khởi hành, và được hoàn lại tiền đã thanh toán sau khi admin duyệt.
            - Trong vòng 48 giờ: KHÔNG huỷ trực tuyến được, khách phải liên hệ hỗ trợ.
            - Giờ nhận phòng mặc định 14:00, trả phòng 12:00.
            Nếu không chắc chắn hoặc ngoài phạm vi, hãy khuyên khách bấm "Gặp tổng đài viên". Tuyệt đối không bịa thông tin đơn hàng cụ thể.
            """;

    private final String provider;
    private final String apiKey;
    private final String model;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public SupportAiClient(
            @Value("${app.support.llm.provider:openai}") String provider,
            @Value("${app.support.llm.api-key:}") String apiKey,
            @Value("${app.support.llm.model:gpt-4o-mini}") String model) {
        this.provider = provider == null ? "openai" : provider.trim().toLowerCase();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "gpt-4o-mini" : model.trim();
    }

    /** Có bật nhánh LLM không (đã cấu hình api-key). */
    public boolean isEnabled() {
        return !apiKey.isBlank();
    }

    /**
     * Hỏi LLM. Trả về câu trả lời, hoặc Optional.empty() nếu chưa cấu hình / lỗi / rỗng.
     * KHÔNG bao giờ ném ngoại lệ.
     */
    public Optional<String> ask(String question, Locale locale) {
        if (apiKey.isBlank() || question == null || question.isBlank()) {
            return Optional.empty();
        }
        String system = SYSTEM_PROMPT + "\n" + languageInstruction(locale);
        try {
            return "anthropic".equals(provider) ? askAnthropic(question, system) : askOpenAi(question, system);
        } catch (Exception ex) {
            log.warn("Support LLM call failed ({}): {}", provider, ex.toString());
            return Optional.empty();
        }
    }

    /** Yêu cầu LLM trả lời đúng ngôn ngữ người dùng đang dùng (vi/en/zh). */
    private static String languageInstruction(Locale locale) {
        String lang = locale == null ? "vi" : locale.getLanguage();
        switch (lang) {
            case "en": return "IMPORTANT: Reply in English.";
            case "zh": return "重要：请用中文回答。";
            default:   return "QUAN TRỌNG: Trả lời bằng tiếng Việt.";
        }
    }

    private Optional<String> askOpenAi(String question, String system) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0.3);
        ArrayNode messages = body.putArray("messages");
        messages.add(msg("system", system));
        messages.add(msg("user", question));

        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/chat/completions"))
                .timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            log.warn("OpenAI HTTP {}: {}", res.statusCode(), trim(res.body()));
            return Optional.empty();
        }
        JsonNode root = mapper.readTree(res.body());
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        return content.isTextual() ? nonBlank(content.asText()) : Optional.empty();
    }

    private Optional<String> askAnthropic(String question, String system) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", 400);
        body.put("system", system);
        ArrayNode messages = body.putArray("messages");
        messages.add(msg("user", question));

        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages"))
                .timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            log.warn("Anthropic HTTP {}: {}", res.statusCode(), trim(res.body()));
            return Optional.empty();
        }
        JsonNode root = mapper.readTree(res.body());
        JsonNode text = root.path("content").path(0).path("text");
        return text.isTextual() ? nonBlank(text.asText()) : Optional.empty();
    }

    private ObjectNode msg(String role, String content) {
        ObjectNode n = mapper.createObjectNode();
        n.put("role", role);
        n.put("content", content);
        return n;
    }

    private static Optional<String> nonBlank(String s) {
        return (s == null || s.isBlank()) ? Optional.empty() : Optional.of(s.trim());
    }

    private static String trim(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) : s;
    }
}
