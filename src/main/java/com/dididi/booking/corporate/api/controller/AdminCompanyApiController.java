package com.dididi.booking.corporate.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.corporate.api.dto.CompanyBookingDto;
import com.dididi.booking.corporate.api.dto.CompanyDto;
import com.dididi.booking.corporate.api.dto.CompanyEmployeeDto;
import com.dididi.booking.corporate.api.dto.CompanyUpsertRequest;
import com.dididi.booking.corporate.service.CompanyService;
import com.dididi.booking.invite.api.dto.CompanyInviteDto;
import com.dididi.booking.invite.api.dto.CreateInviteRequest;
import com.dididi.booking.invite.service.CompanyInviteService;
import org.springframework.security.core.Authentication;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "Admin - Công ty (B2B)", description = "ADMIN/SUPER_ADMIN quản công ty, hạn mức, nhân viên, chi tiêu.")
@RestController
@RequestMapping("/api/admin/v1/companies")
public class AdminCompanyApiController {

    private final CompanyService companyService;
    private final CompanyInviteService inviteService;

    public AdminCompanyApiController(CompanyService companyService, CompanyInviteService inviteService) {
        this.companyService = companyService;
        this.inviteService = inviteService;
    }

    @Operation(summary = "Danh sách công ty")
    @GetMapping
    public ApiResponse<List<CompanyDto>> list() {
        return ApiResponse.ok(companyService.list().stream().map(CompanyDto::from).toList());
    }

    @Operation(summary = "Chi tiết công ty")
    @GetMapping("/{id}")
    public ApiResponse<CompanyDto> get(@PathVariable Long id) {
        return ApiResponse.ok(CompanyDto.from(companyService.get(id)));
    }

    @Operation(summary = "Tạo công ty")
    @PostMapping
    public ApiResponse<CompanyDto> create(@RequestBody CompanyUpsertRequest req) {
        return ApiResponse.ok(CompanyDto.from(companyService.create(req)), "Đã tạo công ty");
    }

    @Operation(summary = "Cập nhật công ty (tên, mã, hạn mức tổng, email, trạng thái)")
    @PutMapping("/{id}")
    public ApiResponse<CompanyDto> update(@PathVariable Long id, @RequestBody CompanyUpsertRequest req) {
        return ApiResponse.ok(CompanyDto.from(companyService.update(id, req)), "Đã cập nhật");
    }

    @Operation(summary = "Nạp thêm hạn mức (cộng vào hạn mức tổng)")
    @PostMapping("/{id}/topup")
    public ApiResponse<CompanyDto> topUp(@PathVariable Long id, @RequestParam BigDecimal amount) {
        return ApiResponse.ok(CompanyDto.from(companyService.topUp(id, amount)), "Đã nạp thêm hạn mức");
    }

    @Operation(summary = "Danh sách nhân viên của công ty")
    @GetMapping("/{id}/employees")
    public ApiResponse<List<CompanyEmployeeDto>> employees(@PathVariable Long id) {
        return ApiResponse.ok(companyService.listEmployees(id).stream().map(CompanyEmployeeDto::from).toList());
    }

    @Operation(summary = "Gán nhân viên (userId) vào công ty")
    @PostMapping("/{id}/employees/{userId}")
    public ApiResponse<Void> assign(@PathVariable Long id, @PathVariable Long userId) {
        companyService.assignEmployee(id, userId);
        return ApiResponse.ok(null, "Đã gán nhân viên");
    }

    @Operation(summary = "Gỡ nhân viên khỏi công ty")
    @DeleteMapping("/{id}/employees/{userId}")
    public ApiResponse<Void> unassign(@PathVariable Long id, @PathVariable Long userId) {
        companyService.unassignEmployee(id, userId);
        return ApiResponse.ok(null, "Đã gỡ nhân viên");
    }

    @Operation(summary = "Đơn đặt chi từ ngân sách công ty (báo cáo chi tiêu)")
    @GetMapping("/{id}/bookings")
    public ApiResponse<List<CompanyBookingDto>> bookings(@PathVariable Long id) {
        return ApiResponse.ok(companyService.companyBookings(id).stream().map(CompanyBookingDto::from).toList());
    }

    // ---------------- Lời mời booker (B2B) ----------------

    @Operation(summary = "Mời 1 người (email) tham gia công ty - trả về link chấp nhận")
    @PostMapping("/{id}/invites")
    public ApiResponse<CompanyInviteDto> invite(@PathVariable Long id, @RequestBody CreateInviteRequest req,
                                                Authentication auth) {
        Long actor = Long.valueOf(auth.getName());
        return ApiResponse.ok(inviteService.create(id, req.email(), actor), "Đã tạo lời mời");
    }

    @Operation(summary = "Danh sách lời mời của công ty")
    @GetMapping("/{id}/invites")
    public ApiResponse<List<CompanyInviteDto>> invites(@PathVariable Long id) {
        return ApiResponse.ok(inviteService.list(id));
    }

    @Operation(summary = "Thu hồi 1 lời mời")
    @DeleteMapping("/{id}/invites/{inviteId}")
    public ApiResponse<Void> revokeInvite(@PathVariable Long id, @PathVariable Long inviteId) {
        inviteService.revoke(id, inviteId);
        return ApiResponse.ok(null, "Đã thu hồi lời mời");
    }
}
