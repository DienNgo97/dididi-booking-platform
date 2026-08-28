package com.dididi.booking.wallet.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.dto.PagedResponse;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.wallet.api.dto.WalletDtos.PayoutDto;
import com.dididi.booking.wallet.domain.entity.PayoutRequest;
import com.dididi.booking.wallet.domain.enums.PayoutStatus;
import com.dididi.booking.wallet.repository.PayoutRequestRepository;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
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

    public AdminPayoutApiController(PayoutRequestRepository payoutRepository, UserRepository userRepository) {
        this.payoutRepository = payoutRepository;
        this.userRepository = userRepository;
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
}
