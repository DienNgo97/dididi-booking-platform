package com.dididi.booking.wallet.api.controller;

import com.dididi.booking.audit.event.AuditEvent;
import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.dto.PagedResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.wallet.api.dto.WalletDtos.BankAccountRequest;
import com.dididi.booking.wallet.api.dto.WalletDtos.LedgerEntryDto;
import com.dididi.booking.wallet.api.dto.WalletDtos.PayoutCreateRequest;
import com.dididi.booking.wallet.api.dto.WalletDtos.PayoutDto;
import com.dididi.booking.wallet.api.dto.WalletDtos.WalletSummaryDto;
import com.dididi.booking.wallet.domain.entity.PayoutRequest;
import com.dididi.booking.wallet.service.VendorWalletService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ví doanh thu của VENDOR (VW5). Mọi endpoint lấy vendorId TỪ AUTHENTICATION —
 * không nhận id từ client (chống IDOR, cùng nguyên tắc VendorHotelApiController).
 */
@RestController
@RequestMapping("/api/vendor/v1/wallet")
public class VendorWalletApiController {

    private final VendorWalletService walletService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;

    public VendorWalletApiController(VendorWalletService walletService,
                                     UserRepository userRepository,
                                     ApplicationEventPublisher events) {
        this.walletService = walletService;
        this.userRepository = userRepository;
        this.events = events;
    }

    @Operation(summary = "Tổng quan ví: số dư khả dụng / đang chờ / bị giữ + tài khoản nhận tiền (che số)")
    @GetMapping
    public ApiResponse<WalletSummaryDto> summary(Authentication auth) {
        Long vendorId = currentUserId(auth);
        User u = userRepository.findById(vendorId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy tài khoản", HttpStatus.NOT_FOUND));
        return ApiResponse.ok(WalletSummaryDto.of(walletService.summary(vendorId),
                u.getBankName(), u.getBankAccountNo(), u.getBankAccountHolder()));
    }

    @Operation(summary = "Lịch sử bút toán sổ cái (phân trang)")
    @GetMapping("/ledger")
    public ApiResponse<PagedResponse<LedgerEntryDto>> ledger(@RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size,
                                                             Authentication auth) {
        return ApiResponse.ok(PagedResponse.of(
                walletService.ledger(currentUserId(auth), page, size).map(LedgerEntryDto::from)));
    }

    @Operation(summary = "Danh sách yêu cầu rút tiền của tôi (phân trang)")
    @GetMapping("/payouts")
    public ApiResponse<PagedResponse<PayoutDto>> payouts(@RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size,
                                                         Authentication auth) {
        return ApiResponse.ok(PagedResponse.of(
                walletService.payouts(currentUserId(auth), page, size).map(PayoutDto::from)));
    }

    @Operation(summary = "Tạo yêu cầu rút tiền (≥ mức tối thiểu, ≤ khả dụng; tiền bị giữ chỗ ngay)")
    @PostMapping("/payouts")
    public ApiResponse<PayoutDto> create(@RequestBody PayoutCreateRequest req, Authentication auth) {
        Long vendorId = currentUserId(auth);
        PayoutRequest p = walletService.requestPayout(vendorId, req == null ? null : req.amount());
        events.publishEvent(new AuditEvent(vendorId, "PAYOUT_REQUESTED", "PAYOUT", p.getId(),
                "Vendor yêu cầu rút " + p.getAmount() + " " + p.getCurrency()
                        + " về TK " + VendorWalletService.maskAccount(p.getBankAccountNo())));
        return ApiResponse.ok(PayoutDto.from(p), "Đã tạo yêu cầu rút tiền");
    }

    @Operation(summary = "Huỷ yêu cầu rút khi còn đang chờ (REQUESTED)")
    @DeleteMapping("/payouts/{id}")
    public ApiResponse<PayoutDto> cancel(@PathVariable Long id, Authentication auth) {
        Long vendorId = currentUserId(auth);
        PayoutRequest p = walletService.cancelPayout(vendorId, id);
        events.publishEvent(new AuditEvent(vendorId, "PAYOUT_CANCELLED", "PAYOUT", p.getId(),
                "Vendor tự huỷ yêu cầu rút " + p.getAmount() + " " + p.getCurrency()));
        return ApiResponse.ok(PayoutDto.from(p), "Đã huỷ yêu cầu");
    }

    @Operation(summary = "Cập nhật tài khoản ngân hàng nhận tiền")
    @PutMapping("/bank-account")
    public ApiResponse<WalletSummaryDto> updateBank(@RequestBody BankAccountRequest req, Authentication auth) {
        Long vendorId = currentUserId(auth);
        User u = walletService.updateBankAccount(vendorId,
                req == null ? null : req.bankName(),
                req == null ? null : req.accountNo(),
                req == null ? null : req.holder());
        events.publishEvent(new AuditEvent(vendorId, "BANK_ACCOUNT_UPDATED", "USER", vendorId,
                "Vendor cập nhật TK nhận tiền: " + u.getBankName() + " "
                        + VendorWalletService.maskAccount(u.getBankAccountNo())));
        return ApiResponse.ok(WalletSummaryDto.of(walletService.summary(vendorId),
                u.getBankName(), u.getBankAccountNo(), u.getBankAccountHolder()), "Đã lưu tài khoản nhận tiền");
    }

    private Long currentUserId(Authentication auth) {
        if (auth == null) {
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        return Long.valueOf(auth.getName());
    }
}
