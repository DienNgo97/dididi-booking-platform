package com.dididi.booking.common.api;

import com.dididi.booking.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Kiem tra Phase 1 DoD:
 *  - Khong JWT  -> 401 (nam duoi /api/** va khong thuoc /api/auth/**)
 *  - Co JWT hop le -> 200
 */
@RestController
@RequestMapping("/api/v1")
public class PingApiController {

    @GetMapping("/ping")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponse.ok(Map.of("status", "pong"));
    }
}
