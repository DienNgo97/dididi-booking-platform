package com.dididi.booking.wallet.api.dto;

import com.dididi.booking.wallet.domain.entity.PayoutRequest;
import com.dididi.booking.wallet.domain.entity.VendorLedgerEntry;
import com.dididi.booking.wallet.service.VendorWalletService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** DTO ví vendor (VW5) — gom một file cho gọn module. */
public final class WalletDtos {

    private WalletDtos() {}

    /** Tổng quan ví + tài khoản nhận tiền (số TK che, chỉ lộ 4 số cuối). */
    public record WalletSummaryDto(
            BigDecimal total, BigDecimal available, BigDecimal pending,
            BigDecimal held, BigDecimal payoutHolding, BigDecimal minPayout,
            boolean hasBankAccount, String bankName, String bankAccountMasked, String bankAccountHolder) {

        public static WalletSummaryDto of(VendorWalletService.WalletSummary s,
                                          String bankName, String accountNo, String holder) {
            boolean has = bankName != null && !bankName.isBlank()
                    && accountNo != null && !accountNo.isBlank()
                    && holder != null && !holder.isBlank();
            return new WalletSummaryDto(s.total(), s.available(), s.pending(), s.held(), s.payoutHolding(),
                    s.minPayout(), has, bankName,
                    has ? VendorWalletService.maskAccount(accountNo) : null, holder);
        }
    }

    public record LedgerEntryDto(Long id, String type, Long bookingId, String bookingCode,
                                 BigDecimal gross, BigDecimal commissionRate, BigDecimal commissionAmount,
                                 BigDecimal netAmount, LocalDate availableFrom, Instant createdAt) {
        public static LedgerEntryDto from(VendorLedgerEntry e) {
            return new LedgerEntryDto(e.getId(), e.getType().name(), e.getBookingId(), e.getNote(),
                    e.getGross(), e.getCommissionRate(), e.getCommissionAmount(),
                    e.getNetAmount(), e.getAvailableFrom(), e.getCreatedAt());
        }
    }

    public record PayoutDto(Long id, BigDecimal amount, String currency, String status,
                            String bankName, String bankAccountMasked, String bankAccountHolder,
                            String transactionRef, String failReason,
                            Instant createdAt, Instant processedAt,
                            String vendorEmail, String vendorName) {
        public static PayoutDto from(PayoutRequest p) { return from(p, null, null); }

        public static PayoutDto from(PayoutRequest p, String vendorEmail, String vendorName) {
            return new PayoutDto(p.getId(), p.getAmount(), p.getCurrency(), p.getStatus().name(),
                    p.getBankName(), VendorWalletService.maskAccount(p.getBankAccountNo()),
                    p.getBankAccountHolder(), p.getTransactionRef(), p.getFailReason(),
                    p.getCreatedAt(), p.getProcessedAt(), vendorEmail, vendorName);
        }
    }

    public record PayoutCreateRequest(BigDecimal amount) {}

    public record BankAccountRequest(String bankName, String accountNo, String holder) {}
}
