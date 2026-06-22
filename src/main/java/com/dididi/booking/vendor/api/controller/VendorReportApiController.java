package com.dididi.booking.vendor.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.vendor.api.dto.InventoryReportDto;
import com.dididi.booking.vendor.api.dto.RevenueReportDto;
import com.dididi.booking.vendor.api.dto.VendorDashboardDto;
import com.dididi.booking.vendor.service.VendorReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Vendor - Báo cáo", description = "Cần JWT role VENDOR. Doanh thu / tồn kho / dashboard của KS mình.")
@RestController
@RequestMapping("/api/vendor/v1/reports")
public class VendorReportApiController {

    private final VendorReportService reportService;

    public VendorReportApiController(VendorReportService reportService) {
        this.reportService = reportService;
    }

    private Long uid(Authentication auth) {
        if (auth == null) {
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        return Long.valueOf(auth.getName());
    }

    @Operation(summary = "Báo cáo doanh thu theo mức TOTAL | WEEK | MONTH | YEAR")
    @GetMapping("/revenue")
    public ApiResponse<RevenueReportDto> revenue(
            @RequestParam(defaultValue = "TOTAL") String granularity, Authentication auth) {
        return ApiResponse.ok(reportService.revenue(uid(auth), granularity));
    }

    @Operation(summary = "Báo cáo tồn kho cho khoảng ngày (mặc định 30 ngày tới)")
    @GetMapping("/inventory")
    public ApiResponse<InventoryReportDto> inventory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication auth) {
        return ApiResponse.ok(reportService.inventory(uid(auth), from, to));
    }

    @Operation(summary = "Số liệu tổng quan dashboard vendor")
    @GetMapping("/dashboard")
    public ApiResponse<VendorDashboardDto> dashboard(Authentication auth) {
        return ApiResponse.ok(reportService.dashboard(uid(auth)));
    }
}
