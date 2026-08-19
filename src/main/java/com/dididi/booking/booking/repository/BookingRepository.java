package com.dididi.booking.booking.repository;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.domain.enums.BookingType;
import com.dididi.booking.booking.domain.enums.CancelStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /** Tìm kiếm admin theo mã đơn / tiêu đề, kèm lọc status/cancelStatus tuỳ chọn (thanh tìm kiếm tab Đơn đặt). */
    @Query("""
            SELECT b FROM Booking b
            WHERE (lower(b.publicCode) LIKE lower(concat('%', :q, '%'))
                   OR lower(b.title) LIKE lower(concat('%', :q, '%')))
              AND (:status IS NULL OR b.status = :status)
              AND (:cancelStatus IS NULL OR b.cancelStatus = :cancelStatus)
            """)
    Page<Booking> adminSearch(@Param("q") String q,
                              @Param("status") BookingStatus status,
                              @Param("cancelStatus") CancelStatus cancelStatus,
                              Pageable pageable);
    Optional<Booking> findByPublicCode(String publicCode);
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Booking> findByGroupIdOrderByCreatedAtAsc(Long groupId);

    // ---- Scheduler het han thanh toan (BP-BK-04): quet don PENDING_PAYMENT tao truoc 1 moc thoi gian ----
    List<Booking> findByStatusAndCreatedAtBefore(BookingStatus status, Instant createdBefore);

    // ---- Con phong theo khung gio (DIRECT): cac don dang giu phong cua 1 loai phong, giao voi [from, to] ----
    // roomTypeId chi set cho don khach san DIRECT (don PMS/flight = null) nen loc theo roomTypeId la du.
    @Query("select b from Booking b where b.roomTypeId = :rtId and b.status in :statuses "
            + "and b.checkIn <= :to and b.checkOut >= :from")
    List<Booking> findActiveForRoomType(@Param("rtId") Long rtId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to,
                                        @Param("statuses") Collection<BookingStatus> statuses);

    // ---- Admin (Phase 4b) ----
    Page<Booking> findByStatus(BookingStatus status, Pageable pageable);
    Page<Booking> findByCancelStatus(CancelStatus cancelStatus, Pageable pageable);
    long countByStatus(BookingStatus status);
    List<Booking> findTop5ByOrderByCreatedAtDesc();

    // ---- Corporate B2B (Dot 3) ----
    List<Booking> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    // ---- Commission report (Dot 3) ----
    List<Booking> findByStatusOrderByCreatedAtDesc(BookingStatus status);

    // ---- Vendor report: don cua 1 KS (targetId = hotelId) theo trang thai ----
    List<Booking> findByTypeAndTargetIdAndStatus(BookingType type, Long targetId, BookingStatus status);

    // ---- Voucher usage (nice-to-have) ----
    long countByVoucherCodeAndStatus(String voucherCode, BookingStatus status);
    long countByUserIdAndVoucherCodeAndStatus(Long userId, String voucherCode, BookingStatus status);
}
