package com.dididi.booking.promo.api;

import com.dididi.booking.audit.event.AuditEvent;
import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.dto.PagedResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.promo.api.dto.PromoCampaignDto;
import com.dididi.booking.promo.api.dto.PromoGrantDto;
import com.dididi.booking.promo.domain.PromoCampaign;
import com.dididi.booking.promo.domain.PromoCampaignType;
import com.dididi.booking.promo.domain.PromoGrant;
import com.dididi.booking.promo.repository.PromoCampaignRepository;
import com.dididi.booking.promo.repository.PromoGrantRepository;
import com.dididi.booking.promo.service.PersonalPromoService;
import com.dididi.booking.voucher.domain.VoucherDiscountType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ADMIN — quản lý chương trình khuyến mãi CÁ NHÂN HOÁ:
 * bật/tắt, chỉnh mức giảm & điều kiện, xem lịch sử phát, chạy thủ công (không chờ lịch).
 */
@Tag(name = "Admin - Khuyến mãi cá nhân hoá")
@RestController
@RequestMapping("/api/admin/v1/promo")
public class AdminPromoApiController {

    private final PromoCampaignRepository campaignRepository;
    private final PromoGrantRepository grantRepository;
    private final PersonalPromoService promoService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;

    public AdminPromoApiController(PromoCampaignRepository campaignRepository, PromoGrantRepository grantRepository,
                                   PersonalPromoService promoService, UserRepository userRepository,
                                   ApplicationEventPublisher events) {
        this.campaignRepository = campaignRepository;
        this.grantRepository = grantRepository;
        this.promoService = promoService;
        this.userRepository = userRepository;
        this.events = events;
    }

    private static Long actorId(Authentication auth) {
        try { return auth == null ? null : Long.valueOf(auth.getName()); } catch (Exception e) { return null; }
    }

    // ---------------- Chương trình ----------------

    @Operation(summary = "Danh sách chương trình + số voucher đã phát")
    @GetMapping("/campaigns")
    public ApiResponse<List<PromoCampaignDto>> campaigns() {
        List<PromoCampaignDto> out = new ArrayList<>();
        for (PromoCampaign c : campaignRepository.findAllByOrderByIdAsc()) {
            out.add(PromoCampaignDto.from(c, grantRepository.countByType(c.getType())));
        }
        return ApiResponse.ok(out);
    }

    @Operation(summary = "Bật/tắt 1 chương trình")
    @PutMapping("/campaigns/{type}/enabled")
    @Transactional
    public ApiResponse<PromoCampaignDto> toggle(@PathVariable String type,
                                                @RequestParam boolean enabled,
                                                Authentication auth) {
        PromoCampaign c = campaign(type);
        c.setEnabled(enabled);
        campaignRepository.save(c);
        events.publishEvent(new AuditEvent(actorId(auth), enabled ? "ENABLE_PROMO" : "DISABLE_PROMO",
                "PROMO_CAMPAIGN", c.getId(), c.getType().name()));
        return ApiResponse.ok(PromoCampaignDto.from(c, grantRepository.countByType(c.getType())),
                enabled ? "Đã bật chương trình" : "Đã tắt chương trình");
    }

    @Operation(summary = "Cập nhật cấu hình 1 chương trình")
    @PutMapping("/campaigns/{type}")
    @Transactional
    public ApiResponse<PromoCampaignDto> update(@PathVariable String type,
                                                @RequestBody Map<String, Object> body,
                                                Authentication auth) {
        PromoCampaign c = campaign(type);
        if (body.containsKey("title")) c.setTitle(str(body.get("title"), c.getTitle()));
        if (body.containsKey("description")) c.setDescription(str(body.get("description"), c.getDescription()));
        if (body.containsKey("discountType")) {
            try {
                c.setDiscountType(VoucherDiscountType.valueOf(String.valueOf(body.get("discountType")).toUpperCase()));
            } catch (Exception ignored) { }
        }
        if (body.containsKey("discountValue")) c.setDiscountValue(dec(body.get("discountValue"), c.getDiscountValue()));
        if (body.containsKey("maxDiscount")) c.setMaxDiscount(decOrNull(body.get("maxDiscount")));
        if (body.containsKey("minOrderAmount")) c.setMinOrderAmount(decOrNull(body.get("minOrderAmount")));
        if (body.containsKey("validDays")) c.setValidDays(Math.max(1, num(body.get("validDays"), c.getValidDays())));
        if (body.containsKey("thresholdDays")) c.setThresholdDays(Math.max(0, num(body.get("thresholdDays"), c.getThresholdDays())));
        if (body.containsKey("minTier")) c.setMinTier(str(body.get("minTier"), c.getMinTier()));
        if (c.getDiscountValue() == null || c.getDiscountValue().signum() <= 0) {
            throw new BusinessException("INVALID_VALUE", "Mức giảm phải lớn hơn 0", HttpStatus.BAD_REQUEST);
        }
        if (c.getDiscountType() == VoucherDiscountType.PERCENT && c.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException("INVALID_VALUE", "Giảm theo % không thể vượt 100", HttpStatus.BAD_REQUEST);
        }
        campaignRepository.save(c);
        events.publishEvent(new AuditEvent(actorId(auth), "UPDATE_PROMO", "PROMO_CAMPAIGN", c.getId(), c.getType().name()));
        return ApiResponse.ok(PromoCampaignDto.from(c, grantRepository.countByType(c.getType())), "Đã lưu cấu hình");
    }

    @Operation(summary = "Chạy NGAY 1 chương trình (không chờ lịch 8:05 sáng)")
    @PostMapping("/campaigns/{type}/run")
    public ApiResponse<Map<String, Object>> run(@PathVariable String type, Authentication auth) {
        PromoCampaignType t = parseType(type);
        int granted = switch (t) {
            case BIRTHDAY -> promoService.runBirthday();
            case WIN_BACK -> promoService.runWinBack();
            case TIER_REWARD -> promoService.runTierReward();
            case WELCOME -> promoService.runWelcome();
        };
        events.publishEvent(new AuditEvent(actorId(auth), "RUN_PROMO", "PROMO_CAMPAIGN", null,
                t.name() + " -> " + granted + " voucher"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", t.name());
        out.put("granted", granted);
        return ApiResponse.ok(out, "Đã phát " + granted + " voucher");
    }

    // ---------------- Lịch sử phát ----------------

    @Operation(summary = "Lịch sử phát voucher cá nhân hoá (lọc theo chương trình)")
    @GetMapping("/grants")
    public ApiResponse<PagedResponse<PromoGrantDto>> grants(@RequestParam(required = false) String type,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        int p = Math.max(0, page);
        int s = Math.min(Math.max(1, size), 100);
        Page<PromoGrant> found = (type == null || type.isBlank())
                ? grantRepository.findAllByOrderByIdDesc(PageRequest.of(p, s))
                : grantRepository.findByTypeOrderByIdDesc(parseType(type), PageRequest.of(p, s));

        // nạp thông tin khách theo LÔ (tránh N+1)
        List<Long> ids = found.getContent().stream().map(PromoGrant::getUserId).distinct().toList();
        Map<Long, User> users = new HashMap<>();
        for (User u : userRepository.findAllById(ids)) users.put(u.getId(), u);

        Page<PromoGrantDto> dto = found.map(g -> {
            User u = users.get(g.getUserId());
            return PromoGrantDto.from(g, u == null ? null : u.getEmail(), u == null ? null : u.getFullName());
        });
        return ApiResponse.ok(PagedResponse.of(dto));
    }

    // ---------------- helpers ----------------

    private PromoCampaign campaign(String type) {
        return campaignRepository.findByType(parseType(type))
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy chương trình", HttpStatus.NOT_FOUND));
    }

    private PromoCampaignType parseType(String type) {
        try {
            return PromoCampaignType.valueOf(type.trim().toUpperCase());
        } catch (Exception e) {
            throw new BusinessException("INVALID_TYPE", "Mã chương trình không hợp lệ", HttpStatus.BAD_REQUEST);
        }
    }

    private static String str(Object v, String fallback) {
        return v == null ? fallback : String.valueOf(v).trim();
    }

    private static int num(Object v, int fallback) {
        try { return Integer.parseInt(String.valueOf(v).trim()); } catch (Exception e) { return fallback; }
    }

    private static BigDecimal dec(Object v, BigDecimal fallback) {
        try { return new BigDecimal(String.valueOf(v).trim()); } catch (Exception e) { return fallback; }
    }

    private static BigDecimal decOrNull(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try { return new BigDecimal(String.valueOf(v).trim()); } catch (Exception e) { return null; }
    }
}
