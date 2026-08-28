package com.dididi.booking.wallet.api.controller;

import com.dididi.booking.audit.event.AuditEvent;
import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.common.dto.PagedResponse;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.wallet.api.dto.WalletDtos.PayoutDto;
import com.dididi.booking.wallet.domain.entity.PayoutRequest;
import com.dididi.booking.wallet.domain.enums.PayoutStatus;
import com.dididi.booking.wallet.repository.PayoutRequestRepository;
import com.dididi.booking.wallet.service.VendorWalletService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admin GIÁM SÁT rút tiền ví vendor (VW5): dev/demo mock ngân hàng tự chi nên admin chỉ đọc;
 * prod (mock tắt) danh sách này là hàng đợi để admin chuyển khoản tay ngoài hệ thống.
 */
@RestController
@RequestMapping("/api/admin/v1/payouts")
public class AdminPayoutApiController {

    private final PayoutRequestRepository payoutRepository;
    private final UserRepository userRepository;
    private final VendorWalletService walletService;
    private final ApplicationEventPublisher events;

    public AdminPayoutApiController(PayoutRequestRepository payoutRepository, UserRepository userRepository,
                                    VendorWalletService walletService, ApplicationEventPublisher events) {
        this.payoutRepository = payoutRepository;
        this.userRepository = userRepository;
        this.walletService = walletService;
        this.events = events;
    }

    @Operation(summary = "Danh sách yêu cầu rút tiền (mọi vendor, lọc status tuỳ chọn)")
    @GetMapping
    public ApiResponse<PagedResponse<PayoutDto>> list(@RequestParam(required = false) PayoutStatus status,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 50));
        Page<PayoutRequest> result = (status == null)
                ? payoutRepository.findAllByOrderByIdDesc(pageable)
                : payoutRepository.findByStatusOrderByIdDesc(status, pageable);

        Set<Long> vendorIds = result.getContent().stream()
                .map(PayoutRequest::getVendorId).collect(Collectors.toSet());
        Map<Long, User> vendors = userRepository.findAllById(vendorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        Page<PayoutDto> dto = result.map(p -> {
            User v = vendors.get(p.getVendorId());
            return PayoutDto.from(p, v == null ? null : v.getEmail(), v == null ? null : v.getFullName());
        });
        return ApiResponse.ok(PagedResponse.of(dto));
    }

    public record SettleRequest(String transactionRef, String reason) {}

    @Operation(summary = "Ghi nhận ĐÃ chuyển khoản cho vendor (admin chi tay ngoài hệ thống, kèm mã UNC)")
    @PostMapping("/{id}/paid")
    public ApiResponse<PayoutDto> markPaid(@PathVariable Long id,
                                           @RequestBody(required = false) SettleRequest req,
                                           Authentication auth) {
        PayoutRequest p = walletService.adminMarkPaid(id, req == null ? null : req.transactionRef());
        events.publishEvent(new AuditEvent(userId(auth), "PAYOUT_PAID", "PAYOUT", p.getId(),
                "Admin chi " + p.getAmount().toBigInteger() + " " + p.getCurrency()
                        + " cho vendor #" + p.getVendorId() + " — mã GD " + p.getTransactionRef()));
        return ApiResponse.ok(PayoutDto.from(p), "Đã ghi nhận chi tiền");
    }

    @Operation(summary = "Từ chối / ghi nhận chuyển khoản thất bại — tiền nhả về số dư khả dụng của vendor")
    @PostMapping("/{id}/failed")
    public ApiResponse<PayoutDto> markFailed(@PathVariable Long id,
                                             @RequestBody(required = false) SettleRequest req,
                                             Authentication auth) {
        PayoutRequest p = walletService.adminMarkFailed(id, req == null ? null : req.reason());
        events.publishEvent(new AuditEvent(userId(auth), "PAYOUT_FAILED", "PAYOUT", p.getId(),
                "Admin từ chối yêu cầu rút " + p.getAmount().toBigInteger() + " " + p.getCurrency()
                        + " của vendor #" + p.getVendorId() + " — lý do: " + p.getFailReason()));
        return ApiResponse.ok(PayoutDto.from(p), "Đã ghi nhận thất bại — tiền trả về ví vendor");
    }

    private Long userId(Authentication auth) {
        if (auth == null) {
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        return Long.valueOf(auth.getName());
    }
}
