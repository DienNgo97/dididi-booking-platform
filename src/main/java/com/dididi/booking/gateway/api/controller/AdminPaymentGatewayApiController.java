package com.dididi.booking.gateway.api.controller;

import com.dididi.booking.audit.event.AuditEvent;
import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.security.RoleUtils;
import com.dididi.booking.gateway.api.dto.PaymentGatewayConfigDto;
import com.dididi.booking.gateway.api.dto.PaymentGatewayUpdateRequest;
import com.dididi.booking.gateway.service.PaymentGatewayConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Cổng thanh toán (VNPay)", description = "Chỉ SUPER_ADMIN. hashSecret được che khi đọc.")
@RestController
@RequestMapping("/api/admin/v1/payment-gateway")
public class AdminPaymentGatewayApiController {

    private final PaymentGatewayConfigService service;
    private final ApplicationEventPublisher events;

    public AdminPaymentGatewayApiController(PaymentGatewayConfigService service, ApplicationEventPublisher events) {
        this.service = service;
        this.events = events;
    }

    @Operation(summary = "Xem cấu hình VNPay hiện tại (hashSecret che) - SUPER_ADMIN")
    @GetMapping
    public ApiResponse<PaymentGatewayConfigDto> get(Authentication auth) {
        RoleUtils.requireSuperAdmin(auth);
        return ApiResponse.ok(PaymentGatewayConfigDto.from(service.current()));
    }

    @Operation(summary = "Cập nhật cấu hình VNPay (để trống hashSecret = giữ nguyên) - SUPER_ADMIN")
    @PutMapping
    public ApiResponse<PaymentGatewayConfigDto> update(@RequestBody PaymentGatewayUpdateRequest req, Authentication auth) {
        RoleUtils.requireSuperAdmin(auth);
        var saved = service.update(req.tmnCode(), req.hashSecret(), req.payUrl(), req.returnUrl(), req.enabled());
        events.publishEvent(new AuditEvent(Long.valueOf(auth.getName()),
                "CHANGE_PAYMENT_GATEWAY", "GATEWAY", null, "provider=VNPAY, enabled=" + saved.isEnabled()));
        return ApiResponse.ok(PaymentGatewayConfigDto.from(saved), "Đã cập nhật cấu hình cổng thanh toán");
    }
}
