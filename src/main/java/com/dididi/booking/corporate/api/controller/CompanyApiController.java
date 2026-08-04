package com.dididi.booking.corporate.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.corporate.domain.entity.Company;
import com.dididi.booking.corporate.service.CompanyService;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.invite.domain.CompanyInvite;
import com.dididi.booking.invite.service.CompanyInviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ngân sách công ty (B2B) + nhận lời mời công ty cho Flutter. Dùng lại CompanyService /
 * CompanyInviteService như bản web.
 */
@Tag(name = "Company (khách B2B)")
@RestController
@RequestMapping("/api/v1")
public class CompanyApiController {

    private final CompanyService companyService;
    private final CompanyInviteService inviteService;
    private final UserRepository userRepository;

    public CompanyApiController(CompanyService companyService, CompanyInviteService inviteService,
                               UserRepository userRepository) {
        this.companyService = companyService;
        this.inviteService = inviteService;
        this.userRepository = userRepository;
    }

    private Long uid(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        return Long.valueOf(auth.getName());
    }

    @Operation(summary = "Công ty của tôi (ngân sách còn lại) — null nếu chưa thuộc công ty nào")
    @GetMapping("/company/me")
    public ApiResponse<Map<String, Object>> myCompany(Authentication auth) {
        Company c = companyService.forUser(uid(auth)).orElse(null);
        if (c == null) return ApiResponse.ok(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", c.getName());
        out.put("code", c.getCode());
        out.put("budgetTotal", c.getBudgetTotal());
        out.put("budgetUsed", c.getBudgetUsed());
        out.put("budgetRemaining", c.remaining());
        return ApiResponse.ok(out);
    }

    @Operation(summary = "Xem lời mời công ty theo token (để hiển thị trước khi chấp nhận)")
    @GetMapping("/company-invite/{token}")
    public ApiResponse<Map<String, Object>> viewInvite(@PathVariable String token) {
        CompanyInvite i = inviteService.findByToken(token);
        if (i == null) {
            throw new BusinessException("INVITE_NOT_FOUND", "Không tìm thấy lời mời", HttpStatus.NOT_FOUND);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("token", i.getToken());
        out.put("companyId", i.getCompanyId());
        out.put("email", i.getEmail());
        out.put("status", i.getStatus().name());
        out.put("expiresAt", i.getExpiresAt());
        return ApiResponse.ok(out);
    }

    @Operation(summary = "Chấp nhận lời mời công ty (kích hoạt thanh toán ngân sách công ty)")
    @PostMapping("/company-invite/{token}/accept")
    public ApiResponse<Map<String, Object>> acceptInvite(@PathVariable String token, Authentication auth) {
        User user = userRepository.findById(uid(auth))
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        String companyName = inviteService.accept(token, user);
        return ApiResponse.ok(Map.of("companyName", companyName), "Đã tham gia công ty " + companyName);
    }
}
