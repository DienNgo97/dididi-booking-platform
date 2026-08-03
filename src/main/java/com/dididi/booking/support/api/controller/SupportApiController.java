package com.dididi.booking.support.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.support.dto.SupportAnswer;
import com.dididi.booking.support.service.SupportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * API trợ lý CSKH cho khách (JWT: principal = userId).
 * Dùng lại {@link SupportService#answer} (KB -> LLM -> escalate) như bản web /support/ask.
 * Client (mobile) tự sinh conversationId (cid) và gửi kèm mỗi lượt hỏi để lưu hội thoại.
 */
@Tag(name = "Support (khách)")
@RestController
@RequestMapping("/api/v1/support")
public class SupportApiController {

    private final SupportService supportService;

    public SupportApiController(SupportService supportService) {
        this.supportService = supportService;
    }

    private Long uid(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        return Long.valueOf(auth.getName());
    }

    @Operation(summary = "Hỏi trợ lý CSKH (KB -> LLM -> escalate). Trả về answer/source/escalate.")
    @PostMapping("/ask")
    public ApiResponse<SupportAnswer> ask(@RequestBody Map<String, String> body, Authentication auth) {
        Long userId = uid(auth);
        String q = body.getOrDefault("q", "");
        String cid = body.get("cid");
        String booking = body.get("booking");
        return ApiResponse.ok(supportService.answer(q, cid, userId, booking));
    }
}
