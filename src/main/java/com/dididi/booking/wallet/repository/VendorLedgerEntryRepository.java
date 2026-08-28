package com.dididi.booking.wallet.repository;

import com.dididi.booking.wallet.domain.entity.VendorLedgerEntry;
import com.dididi.booking.wallet.domain.enums.LedgerType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface VendorLedgerEntryRepository extends JpaRepository<VendorLedgerEntry, Long> {

    Page<VendorLedgerEntry> findByVendorIdOrderByIdDesc(Long vendorId, Pageable pageable);

    boolean existsByBookingIdAndType(Long bookingId, LedgerType type);

    boolean existsByPayoutIdAndType(Long payoutId, LedgerType type);

    Optional<VendorLedgerEntry> findByBookingIdAndType(Long bookingId, LedgerType type);

    /** Tổng biến động ròng của ví (mọi bút toán) — số dư tổng. */
    @Query("select coalesce(sum(l.netAmount), 0) from VendorLedgerEntry l where l.vendorId = :vendorId")
    BigDecimal totalBalance(@Param("vendorId") Long vendorId);

    /** Phần đã tới hạn khả dụng (availableFrom <= hôm nay) — CHƯA trừ tiền giữ chỗ payout/khiếu nại. */
    @Query("select coalesce(sum(l.netAmount), 0) from VendorLedgerEntry l " +
            "where l.vendorId = :vendorId and l.availableFrom <= :today")
    BigDecimal maturedBalance(@Param("vendorId") Long vendorId, @Param("today") LocalDate today);

    /**
     * Tiền bị GIỮ do đơn đang có yêu cầu huỷ treo (CancelStatus.REQUESTED) mà bút toán EARNING
     * đã tới hạn khả dụng và CHƯA bị đảo — chờ admin xử lý xong mới nhả/đảo.
     */
    @Query("select coalesce(sum(l.netAmount), 0) from VendorLedgerEntry l " +
            "where l.vendorId = :vendorId and l.type = com.dididi.booking.wallet.domain.enums.LedgerType.EARNING " +
            "and l.availableFrom <= :today " +
            "and exists (select 1 from Booking b where b.id = l.bookingId " +
            "            and b.cancelStatus = com.dididi.booking.booking.domain.enums.CancelStatus.REQUESTED) " +
            "and not exists (select 1 from VendorLedgerEntry r where r.bookingId = l.bookingId " +
            "            and r.type = com.dididi.booking.wallet.domain.enums.LedgerType.REVERSAL)")
    BigDecimal heldByPendingCancel(@Param("vendorId") Long vendorId, @Param("today") LocalDate today);

    /**
     * Đơn HOTEL CONFIRMED của KS có vendor mà CHƯA có bút toán EARNING (scheduler quét theo lô).
     * Trả về cặp [Booking, vendorId]. Lần chạy đầu chính là backfill toàn bộ lịch sử.
     */
    @Query("select b, h.vendorId from Booking b, com.dididi.booking.hotel.domain.entity.Hotel h " +
            "where b.type = com.dididi.booking.booking.domain.enums.BookingType.HOTEL " +
            "and b.status = com.dididi.booking.booking.domain.enums.BookingStatus.CONFIRMED " +
            "and h.id = b.targetId and h.vendorId is not null " +
            "and not exists (select 1 from VendorLedgerEntry l where l.bookingId = b.id " +
            "            and l.type = com.dididi.booking.wallet.domain.enums.LedgerType.EARNING) " +
            "order by b.id asc")
    List<Object[]> findConfirmedHotelBookingsMissingEarning(Pageable pageable);

    /** Các EARNING mà đơn nguồn đã CANCELLED nhưng chưa có bút toán đảo (scheduler quét). */
    @Query("select l from VendorLedgerEntry l " +
            "where l.type = com.dididi.booking.wallet.domain.enums.LedgerType.EARNING " +
            "and not exists (select 1 from VendorLedgerEntry r where r.bookingId = l.bookingId " +
            "            and r.type = com.dididi.booking.wallet.domain.enums.LedgerType.REVERSAL) " +
            "and exists (select 1 from Booking b where b.id = l.bookingId " +
            "            and b.status = com.dididi.booking.booking.domain.enums.BookingStatus.CANCELLED)")
    List<VendorLedgerEntry> findEarningsNeedingReversal();
}
