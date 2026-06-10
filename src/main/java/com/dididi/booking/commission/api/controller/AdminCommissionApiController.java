package com.dididi.booking.commission.api.controller;

import com.dididi.booking.audit.event.AuditEvent;
import com.dididi.booking.commission.api.dto.CommissionConfigDto;
import com.dididi.booking.commission.api.dto.CommissionReportDto;
import com.dididi.booking.commission.api.dto.VendorCommissionDto;
import com.dididi.booking.commission.service.CommissionService;
import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.security.RoleUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "Admin - Hoa hồng (Commission)", description = "Sửa hoa hồng cần SUPER_ADMIN. Báo cáo cho ADMIN/SUPER_ADMIN.")
@RestController
@RequestMapping("/api/admin/v1/commission")
public class AdminCommissionApiController {

    private final CommissionService commissionService;
    private final ApplicationEventPublisher events;

    public AdminCommissionApiController(CommissionService commissionService, ApplicationEventPublisher events) {
        this.commissionService = commissionService;
        this.events = events;
    }

    @Operation(summary = "Tỷ lệ hoa hồng mặc định")
    @GetMapping("/config")
    public ApiResponse<CommissionConfigDto> config() {
        return ApiResponse.ok(new CommissionConfigDto(commissionService.getDefaultRate()));
    }

    @Operation(summary = "Đặt tỷ lệ hoa hồng mặc định (SUPER_ADMIN). rate trong [0,1], vd 0.15")
    @PutMapping("/config")
    public ApiResponse<CommissionConfigDto> setConfig(@RequestParam BigDecimal rate, Authentication auth) {
        RoleUtils.requireSuperAdmin(auth);
        commissionService.setDefaultRate(rate);
        events.publishEvent(new AuditEvent(Long.valueOf(auth.getName()),
                "CHANGE_COMMISSION_DEFAULT", "COMMISSION", null, "rate=" + rate));
        return ApiResponse.ok(new CommissionConfigDto(rate), "Đã cập nhật hoa hồng mặc định");
    }

    @Operation(summary = "Danh sách vendor có hoa hồng riêng")
    @GetMapping("/vendors")
    public ApiResponse<List<VendorCommissionDto>> vendors() {
        return ApiResponse.ok(commissionService.listVendorRates());
    }

    @Operation(summary = "Đặt hoa hồng riêng cho vendor (SUPER_ADMIN)")
    @PutMapping("/vendors/{vendorId}")
    public ApiResponse<Void> setVendor(@PathVariable Long vendorId, @RequestParam BigDecimal rate, Authentication auth) {
        RoleUtils.requireSuperAdmin(auth);
        commissionService.setVendorRate(vendorId, rate);
        events.publishEvent(new AuditEvent(Long.valueOf(auth.getName()),
                "CHANGE_COMMISSION_VENDOR", "VENDOR", vendorId, "rate=" + rate));
        return ApiResponse.ok(null, "Đã đặt hoa hồng cho vendor");
    }

    @Operation(summary = "Gỡ hoa hồng riêng của vendor (về dùng mặc định) (SUPER_ADMIN)")
    @DeleteMapping("/vendors/{vendorId}")
    public ApiResponse<Void> removeVendor(@PathVariable Long vendorId, Authentication auth) {
        RoleUtils.requireSuperAdmin(auth);
        commissionService.removeVendorRate(vendorId);
        events.publishEvent(new AuditEvent(Long.valueOf(auth.getName()),
                "REMOVE_COMMISSION_VENDOR", "VENDOR", vendorId, null));
        return ApiResponse.ok(null, "Đã gỡ hoa hồng riêng");
    }

    @Operation(summary = "Báo cáo hoa hồng theo vendor (đơn HOTEL đã xác nhận)")
    @GetMapping("/report")
    public ApiResponse<CommissionReportDto> report() {
        return ApiResponse.ok(commissionService.report());
    }
}
