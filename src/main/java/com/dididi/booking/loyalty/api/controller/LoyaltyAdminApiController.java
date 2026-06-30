package com.dididi.booking.loyalty.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.common.security.RoleUtils;
import com.dididi.booking.loyalty.api.dto.LoyaltyAccountDto;
import com.dididi.booking.loyalty.api.dto.LoyaltyTxnDto;
import com.dididi.booking.loyalty.service.LoyaltyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Điểm thưởng (Loyalty)", description = "Xem/điều chỉnh điểm thưởng của người dùng (ADMIN/SUPER_ADMIN).")
@RestController
@RequestMapping("/api/admin/v1/loyalty")
public class LoyaltyAdminApiController {

    private final LoyaltyService loyaltyService;

    public LoyaltyAdminApiController(LoyaltyService loyaltyService) {
        this.loyaltyService = loyaltyService;
    }

    @Operation(summary = "Số dư + hạng + lịch sử điểm của 1 người dùng")
    @GetMapping("/{userId}")
    public ApiResponse<LoyaltyAccountDto> account(@PathVariable Long userId) {
        return ApiResponse.ok(new LoyaltyAccountDto(
                userId, loyaltyService.balance(userId), loyaltyService.tier(userId),
                loyaltyService.lifetimeEarned(userId),
                loyaltyService.history(userId).stream().map(LoyaltyTxnDto::from).toList()));
    }

    /** Tran do lon moi lan dieu chinh tay (chong lam dung cong/tru diem khong gioi han). */
    private static final int MAX_ADJUST_MAGNITUDE = 100_000;

    @Operation(summary = "Điều chỉnh điểm (cộng/trừ) cho người dùng — chỉ SUPER_ADMIN, bắt buộc lý do")
    @PostMapping("/{userId}/adjust")
    public ApiResponse<LoyaltyAccountDto> adjust(@PathVariable Long userId, @RequestParam int points,
                                                 @RequestParam(required = false) String note,
                                                 Authentication auth) {
        RoleUtils.requireSuperAdmin(auth);   // SEC-01: chi SUPER_ADMIN duoc dieu chinh diem (quy ra tien)
        if (points == 0) {
            throw new BusinessException("INVALID_POINTS", "Số điểm điều chỉnh phải khác 0", HttpStatus.BAD_REQUEST);
        }
        if (Math.abs(points) > MAX_ADJUST_MAGNITUDE) {
            throw new BusinessException("ADJUST_TOO_LARGE",
                    "Mỗi lần điều chỉnh tối đa " + MAX_ADJUST_MAGNITUDE + " điểm", HttpStatus.BAD_REQUEST);
        }
        if (note == null || note.isBlank()) {
            throw new BusinessException("REASON_REQUIRED", "Vui lòng ghi lý do điều chỉnh điểm", HttpStatus.BAD_REQUEST);
        }
        loyaltyService.adjust(userId, points, note.trim());
        return account(userId);
    }
}
