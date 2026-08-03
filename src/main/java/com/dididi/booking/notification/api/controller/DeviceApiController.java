package com.dididi.booking.notification.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.notification.domain.DeviceToken;
import com.dididi.booking.notification.repository.DeviceTokenRepository;
import com.dididi.booking.notification.service.PushSender;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Đăng ký token thiết bị (FCM) cho user hiện tại để nhận push. JWT: principal = userId. */
@Tag(name = "Devices (push)")
@RestController
@RequestMapping("/api/v1/devices")
public class DeviceApiController {

    private final DeviceTokenRepository repo;
    private final PushSender pushSender;

    public DeviceApiController(DeviceTokenRepository repo, PushSender pushSender) {
        this.repo = repo;
        this.pushSender = pushSender;
    }

    private Long uid(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        return Long.valueOf(auth.getName());
    }

    @Operation(summary = "Đăng ký/cập nhật token thiết bị cho user hiện tại")
    @PostMapping("/token")
    public ApiResponse<Void> register(@RequestBody Map<String, String> body, Authentication auth) {
        Long userId = uid(auth);
        String token = body == null ? null : body.get("token");
        if (token == null || token.isBlank()) {
            throw new BusinessException("BAD_TOKEN", "Thiếu token thiết bị", HttpStatus.BAD_REQUEST);
        }
        DeviceToken dt = repo.findByToken(token).orElseGet(DeviceToken::new);
        dt.setToken(token);
        dt.setUserId(userId);
        dt.setPlatform(body.getOrDefault("platform", "FCM"));
        repo.save(dt);
        return ApiResponse.ok(null, "Đã đăng ký thiết bị");
    }

    @Operation(summary = "Gửi push thử nghiệm cho chính mình + báo số token (chẩn đoán)")
    @PostMapping("/test-push")
    public ApiResponse<List<String>> testPush(Authentication auth) {
        return ApiResponse.ok(pushSender.sendTest(uid(auth)));
    }
}
