package com.dididi.booking.admin.api.controller;

import com.dididi.booking.audit.api.dto.AuditLogDto;
import com.dididi.booking.audit.service.AuditService;
import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.dto.PagedResponse;
import com.dididi.booking.common.security.RoleUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Audit log", description = "Nhật ký hành động. CHỈ SUPER_ADMIN.")
@RestController
@RequestMapping("/api/admin/v1/audit-logs")
public class AdminAuditApiController {

    private final AuditService auditService;

    public AdminAuditApiController(AuditService auditService) {
        this.auditService = auditService;
    }

    @Operation(summary = "Xem nhật ký hành động (lọc theo action tuỳ chọn) - chỉ Super Admin")
    @GetMapping
    public ApiResponse<PagedResponse<AuditLogDto>> list(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            Authentication authentication) {
        RoleUtils.requireSuperAdmin(authentication);
        Page<AuditLogDto> p = auditService.list(action, q, page, size).map(AuditLogDto::from);
        return ApiResponse.ok(PagedResponse.of(p));
    }
}
