package com.dididi.booking.admin.api.controller;

import com.dididi.booking.admin.api.dto.AdminReportDto;
import com.dididi.booking.admin.service.AdminReportService;
import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.security.RoleUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Báo cáo", description = "Báo cáo doanh thu & tăng trưởng người dùng (ADMIN/SUPER_ADMIN).")
@RestController
@RequestMapping("/api/admin/v1/reports")
public class AdminReportApiController {

    private final AdminReportService reportService;

    public AdminReportApiController(AdminReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "Báo cáo theo loại (HOTEL_REVENUE/FLIGHT_REVENUE/COMMISSION/NEW_USERS/NEW_VENDORS) và kỳ (WEEK/MONTH/QUARTER/YEAR). COMMISSION chỉ dành cho SUPER_ADMIN.")
    @GetMapping
    public ApiResponse<AdminReportDto> report(
            @RequestParam(required = false, defaultValue = "HOTEL_REVENUE") String metric,
            @RequestParam(required = false, defaultValue = "MONTH") String granularity,
            Authentication auth) {
        if (metric != null && "COMMISSION".equalsIgnoreCase(metric.trim())) {
            RoleUtils.requireSuperAdmin(auth);   // báo cáo hoa hồng: chỉ Super Admin
        }
        return ApiResponse.ok(reportService.report(metric, granularity));
    }
}
