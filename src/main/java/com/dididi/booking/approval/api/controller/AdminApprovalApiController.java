package com.dididi.booking.approval.api.controller;

import com.dididi.booking.approval.api.dto.ApprovalRequestDto;
import com.dididi.booking.approval.service.ApprovalService;
import com.dididi.booking.audit.event.AuditEvent;
import com.dididi.booking.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin - Phê duyệt đặt B2B", description = "Duyệt/từ chối đơn công ty vượt ngưỡng (ADMIN/SUPER_ADMIN).")
@RestController
@RequestMapping("/api/admin/v1/approvals")
public class AdminApprovalApiController {

    private final ApprovalService approvalService;
    private final ApplicationEventPublisher events;

    public AdminApprovalApiController(ApprovalService approvalService, ApplicationEventPublisher events) {
        this.approvalService = approvalService;
        this.events = events;
    }

    @Operation(summary = "Danh sách yêu cầu phê duyệt đang chờ (lọc theo companyId tuỳ chọn)")
    @GetMapping
    public ApiResponse<List<ApprovalRequestDto>> pending(@RequestParam(required = false) Long companyId) {
        return ApiResponse.ok(approvalService.listPending(companyId));
    }

    @Operation(summary = "Duyệt: trừ ngân sách công ty + xác nhận đơn")
    @PostMapping("/{id}/approve")
    public ApiResponse<ApprovalRequestDto> approve(@PathVariable Long id, Authentication auth) {
        Long actor = Long.valueOf(auth.getName());
        ApprovalRequestDto dto = approvalService.approve(id, actor);
        events.publishEvent(new AuditEvent(actor, "APPROVE_CORP_BOOKING", "APPROVAL", id,
                "booking=" + dto.bookingCode()));
        return ApiResponse.ok(dto, "Đã duyệt và xác nhận đơn");
    }

    @Operation(summary = "Từ chối yêu cầu phê duyệt (đơn vẫn ở trạng thái chờ thanh toán)")
    @PostMapping("/{id}/reject")
    public ApiResponse<ApprovalRequestDto> reject(@PathVariable Long id,
                                                  @RequestParam(required = false) String note,
                                                  Authentication auth) {
        Long actor = Long.valueOf(auth.getName());
        ApprovalRequestDto dto = approvalService.reject(id, actor, note);
        events.publishEvent(new AuditEvent(actor, "REJECT_CORP_BOOKING", "APPROVAL", id,
                "booking=" + dto.bookingCode() + (note != null ? ", note=" + note : "")));
        return ApiResponse.ok(dto, "Đã từ chối yêu cầu");
    }
}
