package com.dididi.booking.ops.api;

import com.dididi.booking.audit.event.AuditEvent;
import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.dto.PagedResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.ops.domain.OpsAlert;
import com.dididi.booking.ops.service.OpsAlertService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** Cảnh báo vận hành cho admin (P0-3/P0-4): xem việc cần xử lý + tick đã xử lý. */
@RestController
@RequestMapping("/api/admin/v1/ops/alerts")
public class AdminOpsAlertApiController {

    private final OpsAlertService service;
    private final ApplicationEventPublisher events;

    public AdminOpsAlertApiController(OpsAlertService service, ApplicationEventPublisher events) {
        this.service = service;
        this.events = events;
    }

    public record AlertDto(Long id, String type, String severity, String status,
                           Long bookingId, String bookingCode, String detail, String suggestedAction,
                           Instant createdAt, Instant resolvedAt, String resolveNote) {
        static AlertDto from(OpsAlert a) {
            return new AlertDto(a.getId(), a.getType().name(), a.getSeverity().name(), a.getStatus().name(),
                    a.getBookingId(), a.getBookingCode(), a.getDetail(), a.getSuggestedAction(),
                    a.getCreatedAt(), a.getResolvedAt(), a.getResolveNote());
        }
    }

    public record ResolveRequest(String note) {}

    @Operation(summary = "Danh sách cảnh báo vận hành (mặc định việc chưa xử lý lên đầu)")
    @GetMapping
    public ApiResponse<PagedResponse<AlertDto>> list(@RequestParam(required = false) OpsAlert.Status status,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PagedResponse.of(service.list(status, page, size).map(AlertDto::from)));
    }

    @Operation(summary = "Số cảnh báo đang mở (cho badge trên menu)")
    @GetMapping("/open-count")
    public ApiResponse<Long> openCount() {
        return ApiResponse.ok(service.openCount());
    }

    @Operation(summary = "Đánh dấu đã xử lý")
    @PostMapping("/{id}/resolve")
    public ApiResponse<AlertDto> resolve(@PathVariable Long id,
                                         @RequestBody(required = false) ResolveRequest req,
                                         Authentication auth) {
        Long adminId = userId(auth);
        OpsAlert a = service.resolve(id, adminId, req == null ? null : req.note());
        events.publishEvent(new AuditEvent(adminId, "OPS_ALERT_RESOLVED", "OPS_ALERT", a.getId(),
                "Xử lý cảnh báo " + a.getType() + " của đơn " + a.getBookingCode() + " — " + a.getResolveNote()));
        return ApiResponse.ok(AlertDto.from(a), "Đã đánh dấu xử lý");
    }

    private Long userId(Authentication auth) {
        if (auth == null) {
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        return Long.valueOf(auth.getName());
    }
}
