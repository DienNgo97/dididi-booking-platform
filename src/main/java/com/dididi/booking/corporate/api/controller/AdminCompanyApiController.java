package com.dididi.booking.corporate.api.controller;

import com.dididi.booking.audit.event.AuditEvent;
import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.security.RoleUtils;
import com.dididi.booking.corporate.api.dto.CompanyBookingDto;
import com.dididi.booking.corporate.api.dto.CompanyDto;
import com.dididi.booking.corporate.api.dto.CompanyEmployeeDto;
import com.dididi.booking.corporate.api.dto.CompanyUpsertRequest;
import com.dididi.booking.corporate.service.CompanyService;
import com.dididi.booking.invite.api.dto.CompanyInviteDto;
import com.dididi.booking.invite.api.dto.CreateInviteRequest;
import com.dididi.booking.invite.service.CompanyInviteService;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher events;

    public AdminCompanyApiController(CompanyService companyService, CompanyInviteService inviteService,
                                     ApplicationEventPublisher events) {
        this.companyService = companyService;
        this.inviteService = inviteService;
        this.events = events;
    }

    /**
     * P1-10: mọi thao tác đụng tới TIỀN của doanh nghiệp đều phải để lại dấu vết — trước đây tạo
     * công ty, đổi hạn mức, nạp tiền, gán/gỡ nhân viên đều không ghi audit, tiền đổi mà không truy
     * được ai làm và làm lúc nào.
     */
    private void audit(Authentication auth, String action, Long companyId, String detail) {
        Long actor = auth == null ? null : Long.valueOf(auth.getName());
        events.publishEvent(new AuditEvent(actor, action, "COMPANY", companyId, detail));
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

    @Operation(summary = "Tạo công ty (SUPER_ADMIN)")
    @PostMapping
    public ApiResponse<CompanyDto> create(@RequestBody CompanyUpsertRequest req, Authentication auth) {
        RoleUtils.requireSuperAdmin(auth);   // SEC-02: tao cong ty + dat han muc tong = anh huong ngan sach
        var c = companyService.create(req);
        audit(auth, "COMPANY_CREATED", c.getId(), "Tạo công ty " + c.getName() + " (" + c.getCode()
                + "), hạn mức tổng " + c.getBudgetTotal().toBigInteger() + " VND");
        return ApiResponse.ok(CompanyDto.from(c), "Đã tạo công ty");
    }

    @Operation(summary = "Cập nhật công ty (tên, mã, hạn mức tổng, email, trạng thái) — SUPER_ADMIN")
    @PutMapping("/{id}")
    public ApiResponse<CompanyDto> update(@PathVariable Long id, @RequestBody CompanyUpsertRequest req,
                                          Authentication auth) {
        RoleUtils.requireSuperAdmin(auth);   // SEC-02: cap nhat han muc tong = anh huong ngan sach
        var truoc = companyService.get(id);
        String hanMucCu = String.valueOf(truoc.getBudgetTotal());
        var c = companyService.update(id, req);
        audit(auth, "COMPANY_UPDATED", id, "Cập nhật công ty " + c.getName()
                + " — hạn mức tổng " + hanMucCu + " -> " + c.getBudgetTotal()
                + ", ngưỡng duyệt " + c.getApprovalThreshold() + ", active=" + c.isActive());
        return ApiResponse.ok(CompanyDto.from(c), "Đã cập nhật");
    }

    @Operation(summary = "Nạp thêm hạn mức (cộng vào hạn mức tổng) — SUPER_ADMIN")
    @PostMapping("/{id}/topup")
    public ApiResponse<CompanyDto> topUp(@PathVariable Long id, @RequestParam BigDecimal amount,
                                         Authentication auth) {
        RoleUtils.requireSuperAdmin(auth);   // SEC-02: nap ngan sach tieu duoc -> chi SUPER_ADMIN
        var c = companyService.topUp(id, amount);
        audit(auth, "COMPANY_BUDGET_TOPUP", id, "Nạp " + amount.toBigInteger() + " VND cho " + c.getName()
                + " — hạn mức tổng sau nạp " + c.getBudgetTotal().toBigInteger() + " VND");
        return ApiResponse.ok(CompanyDto.from(c), "Đã nạp thêm hạn mức");
    }

    @Operation(summary = "Danh sách nhân viên của công ty")
    @GetMapping("/{id}/employees")
    public ApiResponse<List<CompanyEmployeeDto>> employees(@PathVariable Long id) {
        return ApiResponse.ok(companyService.listEmployees(id).stream().map(CompanyEmployeeDto::from).toList());
    }

    @Operation(summary = "Gán nhân viên (userId) vào công ty — SUPER_ADMIN")
    @PostMapping("/{id}/employees/{userId}")
    public ApiResponse<Void> assign(@PathVariable Long id, @PathVariable Long userId, Authentication auth) {
        RoleUtils.requireSuperAdmin(auth);   // SEC-02: gan nhan vien -> cho phep ho tieu ngan sach cong ty
        companyService.assignEmployee(id, userId);
        audit(auth, "COMPANY_EMPLOYEE_ASSIGNED", id,
                "Gán người dùng #" + userId + " vào công ty — từ nay họ tiêu được ngân sách công ty");
        return ApiResponse.ok(null, "Đã gán nhân viên");
    }

    @Operation(summary = "Gỡ nhân viên khỏi công ty")
    @DeleteMapping("/{id}/employees/{userId}")
    public ApiResponse<Void> unassign(@PathVariable Long id, @PathVariable Long userId, Authentication auth) {
        companyService.unassignEmployee(id, userId);
        audit(auth, "COMPANY_EMPLOYEE_UNASSIGNED", id, "Gỡ người dùng #" + userId + " khỏi công ty");
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
        var inv = inviteService.create(id, req.email(), actor);
        audit(auth, "COMPANY_INVITE_CREATED", id, "Mời " + req.email() + " làm booker của công ty");
        return ApiResponse.ok(inv, "Đã tạo lời mời");
    }

    @Operation(summary = "Danh sách lời mời của công ty")
    @GetMapping("/{id}/invites")
    public ApiResponse<List<CompanyInviteDto>> invites(@PathVariable Long id) {
        return ApiResponse.ok(inviteService.list(id));
    }

    @Operation(summary = "Thu hồi 1 lời mời")
    @DeleteMapping("/{id}/invites/{inviteId}")
    public ApiResponse<Void> revokeInvite(@PathVariable Long id, @PathVariable Long inviteId,
                                          Authentication auth) {
        inviteService.revoke(id, inviteId);
        audit(auth, "COMPANY_INVITE_REVOKED", id, "Thu hồi lời mời #" + inviteId);
        return ApiResponse.ok(null, "Đã thu hồi lời mời");
    }
}
