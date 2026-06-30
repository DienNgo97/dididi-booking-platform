package com.dididi.booking.support.web;

import com.dididi.booking.support.domain.SupportRole;
import com.dididi.booking.support.dto.SupportAnswer;
import com.dididi.booking.support.service.SupportService;
import com.dididi.booking.web.CurrentUser;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * Trung tâm hỗ trợ khách hàng:
 *  - GET  /support       : trang hỗ trợ (FAQ + mở trợ lý ảo).
 *  - POST /support/ask    : hỏi trợ lý (JSON) — KB + LLM fallback. Lưu USER+BOT vào DB.
 *  - POST /support/log    : ghi tin nhắn phía client (phần tổng đài, escalate). CSRF miễn (xem SecurityWebConfig).
 */
@Controller
public class SupportController {

    private final SupportService supportService;
    private final CurrentUser currentUser;

    public SupportController(SupportService supportService, CurrentUser currentUser) {
        this.supportService = supportService;
        this.currentUser = currentUser;
    }

    @GetMapping("/support")
    public String page(@RequestParam(required = false) String booking, Model model) {
        model.addAttribute("bookingCode", booking);
        return "support/index";
    }

    @PostMapping("/support/ask")
    @ResponseBody
    public SupportAnswer ask(@RequestParam("q") String q,
                             @RequestParam(required = false) String cid,
                             @RequestParam(required = false) String booking,
                             Authentication auth) {
        return supportService.answer(q, cid, userIdOrNull(auth), booking);
    }

    /** Do dai toi da noi dung 1 tin nhan client gui (clamp them o tang controller cho khach an danh). */
    private static final int MAX_LOG_LEN = 2000;

    @PostMapping("/support/log")
    @ResponseBody
    public Map<String, Object> logMessage(@RequestParam(required = false) String cid,
                                          @RequestParam String role,
                                          @RequestParam String content,
                                          @RequestParam(required = false) String booking,
                                          @RequestParam(defaultValue = "false") boolean escalated,
                                          Authentication auth) {
        Long userId = userIdOrNull(auth);
        boolean anonymous = userId == null;
        // SEC-09: endpoint nay CSRF-exempt + permitAll -> khach an danh khong duoc gia danh AGENT/SYSTEM/BOT,
        // khong duoc danh dau escalated, va noi dung bi clamp. Da dang nhap moi giu role yeu cau.
        SupportRole effectiveRole = anonymous ? SupportRole.USER : parseRole(role);
        boolean effectiveEscalated = !anonymous && escalated;
        String safeContent = clampContent(content);
        supportService.logMessage(cid, effectiveRole, safeContent, userId, booking, effectiveEscalated);
        return Map.of("ok", true);
    }

    private static String clampContent(String s) {
        if (s == null) return "";
        return s.length() > MAX_LOG_LEN ? s.substring(0, MAX_LOG_LEN) : s;
    }

    /** Lấy userId nếu đã đăng nhập; khách vãng lai -> null. Không bao giờ ném lỗi. */
    private Long userIdOrNull(Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
                return null;
            }
            return currentUser.id(auth);
        } catch (Exception ex) {
            return null;
        }
    }

    private static SupportRole parseRole(String role) {
        try {
            return SupportRole.valueOf(role.trim().toUpperCase());
        } catch (Exception ex) {
            return SupportRole.USER;
        }
    }
}
