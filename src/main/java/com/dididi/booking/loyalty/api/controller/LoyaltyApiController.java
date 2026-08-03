package com.dididi.booking.loyalty.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.loyalty.api.dto.LoyaltyTxnDto;
import com.dididi.booking.loyalty.api.dto.RedeemedVoucherDto;
import com.dididi.booking.loyalty.service.LoyaltyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** API điểm thưởng cho khách (JWT: principal = userId). */
@Tag(name = "Loyalty (khách)")
@RestController
@RequestMapping("/api/v1/loyalty")
public class LoyaltyApiController {

    private final LoyaltyService loyaltyService;

    public LoyaltyApiController(LoyaltyService loyaltyService) {
        this.loyaltyService = loyaltyService;
    }

    private Long uid(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        return Long.valueOf(auth.getName());
    }

    @Operation(summary = "Tài khoản điểm thưởng của tôi (số dư, hạng, lịch sử, tỉ lệ đổi)")
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(Authentication auth) {
        Long id = uid(auth);
        List<LoyaltyTxnDto> history = loyaltyService.history(id).stream().map(LoyaltyTxnDto::from).toList();
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("balance", loyaltyService.balance(id));
        out.put("tier", loyaltyService.tier(id));
        out.put("lifetimeEarned", loyaltyService.lifetimeEarned(id));
        out.put("minRedeem", loyaltyService.minRedeem());
        out.put("pointValue", loyaltyService.redeemPointValue());
        out.put("history", history);
        return ApiResponse.ok(out);
    }

    @Operation(summary = "Voucher đã đổi từ điểm")
    @GetMapping("/vouchers")
    public ApiResponse<List<RedeemedVoucherDto>> vouchers(Authentication auth) {
        return ApiResponse.ok(loyaltyService.redeemedVouchers(uid(auth)));
    }

    @Operation(summary = "Đổi điểm lấy voucher giảm giá")
    @PostMapping("/redeem")
    public ApiResponse<Map<String, Object>> redeem(@RequestParam int points, Authentication auth) {
        var v = loyaltyService.redeemForVoucher(uid(auth), points);
        return ApiResponse.ok(Map.of("code", v.getCode(), "value", v.getDiscountValue()), "Đổi điểm thành công");
    }
}
