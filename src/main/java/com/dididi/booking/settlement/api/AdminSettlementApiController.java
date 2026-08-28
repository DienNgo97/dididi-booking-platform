package com.dididi.booking.settlement.api;

import com.dididi.booking.audit.event.AuditEvent;
import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.settlement.domain.PartnerSettlement;
import com.dididi.booking.settlement.service.PartnerSettlementService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Đối soát công nợ B2B với đối tác API (ST3): xem kỳ + phương trình dòng tiền,
 * chốt kỳ, ghi nhận đã thanh toán, xuất file đối soát CSV. Mọi thao tác ghi audit.
 */
@RestController
@RequestMapping("/api/admin/v1/settlements")
public class AdminSettlementApiController {

    private final PartnerSettlementService service;
    private final ApplicationEventPublisher events;

    public AdminSettlementApiController(PartnerSettlementService service, ApplicationEventPublisher events) {
        this.service = service;
        this.events = events;
    }

    @Operation(summary = "Số liệu đối soát 1 kỳ tháng: các dòng đối tác + phương trình dòng tiền")
    @GetMapping
    public ApiResponse<PartnerSettlementService.PeriodView> view(@RequestParam String period) {
        return ApiResponse.ok(service.view(period));
    }

    public record PaidRequest(String paymentRef) {}

    @Operation(summary = "Chốt kỳ cho 1 đối tác (chỉ khi kỳ đã qua + 3 ngày; số chốt bất biến)")
    @PostMapping("/{partnerCode}/close")
    public ApiResponse<PartnerSettlementService.PeriodView> close(@PathVariable String partnerCode,
                                                                  @RequestParam String period,
                                                                  Authentication auth) {
        PartnerSettlement s = service.closePeriod(partnerCode, period, userId(auth));
        events.publishEvent(new AuditEvent(userId(auth), "SETTLEMENT_CLOSED", "SETTLEMENT", s.getId(),
                "Chốt đối soát " + s.getPartnerName() + " kỳ " + period
                        + ": " + s.getBookingCount() + " đơn, phải trả " + s.getNetPayable().toBigInteger() + " VND"));
        return ApiResponse.ok(service.view(period), "Đã chốt kỳ " + period + " cho " + s.getPartnerName());
    }

    @Operation(summary = "Ghi nhận ĐÃ chuyển khoản cho đối tác (kèm mã UNC)")
    @PostMapping("/{partnerCode}/paid")
    public ApiResponse<PartnerSettlementService.PeriodView> paid(@PathVariable String partnerCode,
                                                                 @RequestParam String period,
                                                                 @RequestBody(required = false) PaidRequest req,
                                                                 Authentication auth) {
        PartnerSettlement s = service.markPaid(partnerCode, period, userId(auth),
                req == null ? null : req.paymentRef());
        events.publishEvent(new AuditEvent(userId(auth), "SETTLEMENT_PAID", "SETTLEMENT", s.getId(),
                "Đã thanh toán đối soát " + s.getPartnerName() + " kỳ " + period
                        + ": " + s.getNetPayable().toBigInteger() + " VND — UNC " + s.getPaymentRef()));
        return ApiResponse.ok(service.view(period), "Đã ghi nhận thanh toán cho " + s.getPartnerName());
    }

    @Operation(summary = "File đối soát CSV chi tiết từng đơn của đối tác trong kỳ")
    @GetMapping("/{partnerCode}/export")
    public ResponseEntity<byte[]> export(@PathVariable String partnerCode, @RequestParam String period) {
        String csv = service.exportCsv(partnerCode, period);
        String filename = "doi-soat-" + partnerCode + "-" + period + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    private Long userId(Authentication auth) {
        if (auth == null) {
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        return Long.valueOf(auth.getName());
    }
}
