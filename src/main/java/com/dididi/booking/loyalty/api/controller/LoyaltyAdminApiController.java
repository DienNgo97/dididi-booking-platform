package com.dididi.booking.loyalty.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.loyalty.api.dto.LoyaltyAccountDto;
import com.dididi.booking.loyalty.api.dto.LoyaltyTxnDto;
import com.dididi.booking.loyalty.service.LoyaltyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @Operation(summary = "Điều chỉnh điểm (cộng/trừ) cho người dùng")
    @PostMapping("/{userId}/adjust")
    public ApiResponse<LoyaltyAccountDto> adjust(@PathVariable Long userId, @RequestParam int points,
                                                 @RequestParam(required = false) String note) {
        loyaltyService.adjust(userId, points, note);
        return account(userId);
    }
}
