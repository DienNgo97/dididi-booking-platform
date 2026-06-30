package com.dididi.booking.voucher.api.controller;

import com.dididi.booking.audit.event.AuditEvent;
import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.voucher.api.dto.VoucherDto;
import com.dididi.booking.voucher.api.dto.VoucherUpsertRequest;
import com.dididi.booking.voucher.service.VoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin - Voucher", description = "Quản lý mã giảm giá (ADMIN/SUPER_ADMIN).")
@RestController
@RequestMapping("/api/admin/v1/vouchers")
public class AdminVoucherApiController {

    private final VoucherService voucherService;
    private final ApplicationEventPublisher events;

    public AdminVoucherApiController(VoucherService voucherService, ApplicationEventPublisher events) {
        this.voucherService = voucherService;
        this.events = events;
    }

    private static Long actorId(Authentication auth) {
        try { return auth == null ? null : Long.valueOf(auth.getName()); } catch (Exception e) { return null; }
    }

    @Operation(summary = "Danh sách voucher")
    @GetMapping
    public ApiResponse<List<VoucherDto>> list() {
        return ApiResponse.ok(voucherService.list().stream().map(VoucherDto::from).toList());
    }

    @Operation(summary = "Chi tiết voucher")
    @GetMapping("/{id}")
    public ApiResponse<VoucherDto> get(@PathVariable Long id) {
        return ApiResponse.ok(VoucherDto.from(voucherService.get(id)));
    }

    @Operation(summary = "Tạo voucher. discountType=PERCENT|FIXED")
    @PostMapping
    public ApiResponse<VoucherDto> create(@RequestBody VoucherUpsertRequest req, Authentication auth) {
        VoucherDto dto = VoucherDto.from(voucherService.create(req));
        events.publishEvent(new AuditEvent(actorId(auth), "CREATE_VOUCHER", "VOUCHER", null, "Tạo voucher mới"));
        return ApiResponse.ok(dto, "Đã tạo voucher");
    }

    @Operation(summary = "Cập nhật voucher")
    @PutMapping("/{id}")
    public ApiResponse<VoucherDto> update(@PathVariable Long id, @RequestBody VoucherUpsertRequest req) {
        return ApiResponse.ok(VoucherDto.from(voucherService.update(id, req)), "Đã cập nhật voucher");
    }

    @Operation(summary = "Xoá voucher")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication auth) {
        voucherService.delete(id);
        events.publishEvent(new AuditEvent(actorId(auth), "DELETE_VOUCHER", "VOUCHER", id, "Xoá voucher #" + id));
        return ApiResponse.ok(null, "Đã xoá voucher");
    }
}
