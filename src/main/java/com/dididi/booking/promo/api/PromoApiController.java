package com.dididi.booking.promo.api;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.promo.service.PersonalPromoService;
import com.dididi.booking.voucher.domain.Voucher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** "Ưu đãi của tôi" cho app mobile (JWT: principal = userId). */
@Tag(name = "Ưu đãi cá nhân hoá (khách)")
@RestController
@RequestMapping("/api/v1/offers")
public class PromoApiController {

    private final PersonalPromoService promoService;
    private final com.dididi.booking.voucher.repository.VoucherRedemptionRepository redemptionRepository;
    private final com.dididi.booking.booking.repository.BookingRepository bookingRepository;

    public PromoApiController(PersonalPromoService promoService,
                              com.dididi.booking.voucher.repository.VoucherRedemptionRepository redemptionRepository,
                              com.dididi.booking.booking.repository.BookingRepository bookingRepository) {
        this.promoService = promoService;
        this.redemptionRepository = redemptionRepository;
        this.bookingRepository = bookingRepository;
    }

    /**
     * Mã CÒN dùng được thật hay không: ngoài active + thời hạn, phải tính cả suất đã dùng
     * (VoucherRedemption). Trước đây chỉ xét active+hạn nên mã vừa áp cho một đơn còn hiệu lực
     * vẫn hiện "đang dùng được" — khách bấm dùng lại sẽ bị từ chối VOUCHER_IN_USE.
     * Quy tắc khớp đúng {@code VoucherService.apply}: suất đang gắn đơn PENDING_PAYMENT/CONFIRMED
     * -> hết dùng được; đơn cũ đã huỷ/hết hạn -> suất được trả lại.
     */
    private boolean stillUsable(Voucher v, Long userId) {
        var red = redemptionRepository.findByVoucherCodeAndUserId(v.getCode(), userId).orElse(null);
        if (red == null) {
            return v.getUsageLimit() == null
                    || redemptionRepository.countByVoucherCode(v.getCode()) < v.getUsageLimit();
        }
        Long heldBooking = red.getBookingId();
        if (heldBooking == null) return true;
        var other = bookingRepository.findById(heldBooking).orElse(null);
        if (other == null) return true;
        var st = other.getStatus();
        return st != com.dididi.booking.booking.domain.enums.BookingStatus.PENDING_PAYMENT
                && st != com.dididi.booking.booking.domain.enums.BookingStatus.CONFIRMED;
    }

    private Long uid(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        return Long.valueOf(auth.getName());
    }

    @Operation(summary = "Danh sách voucher riêng của tôi (quà tặng + đổi điểm)")
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> myOffers(Authentication auth) {
        Instant now = Instant.now();
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Voucher v : promoService.myVouchers(uid(auth))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", v.getCode());
            m.put("description", v.getDescription());
            m.put("discountType", v.getDiscountType().name());
            m.put("discountValue", v.getDiscountValue());
            m.put("maxDiscount", v.getMaxDiscount());
            m.put("minOrderAmount", v.getMinOrderAmount());
            m.put("validTo", v.getValidTo());
            boolean usable = v.isActive()
                    && (v.getValidTo() == null || v.getValidTo().isAfter(now))
                    && (v.getValidFrom() == null || !v.getValidFrom().isAfter(now))
                    && stillUsable(v, uid(auth)); // tính cả suất đã dùng, khớp VoucherService.apply
            m.put("usable", usable);
            out.add(m);
        }
        return ApiResponse.ok(out);
    }
}
