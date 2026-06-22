package com.dididi.booking.admin.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.support.dto.ConversationSummaryDto;
import com.dididi.booking.support.dto.SupportMessageDto;
import com.dididi.booking.support.dto.SupportStatsDto;
import com.dididi.booking.support.service.SupportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Xem log & thống kê hội thoại hỗ trợ (chatbot). Bảo vệ bởi chain /api/admin/** -> ADMIN/SUPER_ADMIN.
 */
@Tag(name = "Admin - Support chat", description = "Cần JWT role ADMIN/SUPER_ADMIN")
@RestController
@RequestMapping("/api/admin/v1/support")
public class AdminSupportApiController {

    private final SupportService supportService;

    public AdminSupportApiController(SupportService supportService) {
        this.supportService = supportService;
    }

    @Operation(summary = "Số liệu tổng quan")
    @GetMapping("/stats")
    public ApiResponse<SupportStatsDto> stats() {
        return ApiResponse.ok(supportService.stats());
    }

    @Operation(summary = "Danh sách hội thoại (mới nhất trước)")
    @GetMapping("/conversations")
    public ApiResponse<List<ConversationSummaryDto>> conversations() {
        return ApiResponse.ok(supportService.conversations());
    }

    @Operation(summary = "Các tin nhắn của 1 hội thoại")
    @GetMapping("/messages")
    public ApiResponse<List<SupportMessageDto>> messages(@RequestParam String conversationId) {
        return ApiResponse.ok(supportService.messages(conversationId));
    }
}
