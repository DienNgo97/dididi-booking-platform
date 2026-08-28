package com.dididi.booking.settlement.repository;

import com.dididi.booking.settlement.domain.PartnerSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PartnerSettlementRepository extends JpaRepository<PartnerSettlement, Long> {

    Optional<PartnerSettlement> findByPartnerCodeAndPeriodYm(String partnerCode, String periodYm);

    List<PartnerSettlement> findByPeriodYm(String periodYm);

    // ================= GOM SỐ LIỆU KỲ (tính live từ bảng bookings) =================
    // Quy kỳ theo NGÀY HOÀN THÀNH DỊCH VỤ: khách sạn = checkOut, chuyến bay = travelDate
    // (chuẩn đối soát ngành — doanh thu ghi nhận khi dịch vụ đã cung cấp xong).
    // Chỉ đơn CONFIRMED: đơn CANCELLED đã hoàn tiền khách, không nợ đối tác đồng nào.

    /** KS CHANNEL (đồng bộ hotel-pms), không gắn vendor → công nợ đối tác HOTEL_PMS. */
    @Query("select count(b), coalesce(sum(b.amount), 0) from Booking b, com.dididi.booking.hotel.domain.entity.Hotel h " +
            "where h.id = b.targetId and b.type = com.dididi.booking.booking.domain.enums.BookingType.HOTEL " +
            "and b.status = com.dididi.booking.booking.domain.enums.BookingStatus.CONFIRMED " +
            "and h.vendorId is null and h.source = com.dididi.booking.hotel.domain.enums.HotelSource.CHANNEL " +
            "and b.checkOut between :start and :end")
    List<Object[]> aggregateChannelHotels(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /** KS DIRECT không vendor (tự doanh nền tảng — hiện trong phương trình, không phải trả ai). */
    @Query("select count(b), coalesce(sum(b.amount), 0) from Booking b, com.dididi.booking.hotel.domain.entity.Hotel h " +
            "where h.id = b.targetId and b.type = com.dididi.booking.booking.domain.enums.BookingType.HOTEL " +
            "and b.status = com.dididi.booking.booking.domain.enums.BookingStatus.CONFIRMED " +
            "and h.vendorId is null and h.source = com.dididi.booking.hotel.domain.enums.HotelSource.DIRECT " +
            "and b.checkOut between :start and :end")
    List<Object[]> aggregatePlatformHotels(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /** KS có vendor (đã chảy vào VÍ vendor — hiện trong phương trình để chứng minh không sót). */
    @Query("select count(b), coalesce(sum(b.amount), 0) from Booking b, com.dididi.booking.hotel.domain.entity.Hotel h " +
            "where h.id = b.targetId and b.type = com.dididi.booking.booking.domain.enums.BookingType.HOTEL " +
            "and b.status = com.dididi.booking.booking.domain.enums.BookingStatus.CONFIRMED " +
            "and h.vendorId is not null and b.checkOut between :start and :end")
    List<Object[]> aggregateVendorHotels(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /** Vé máy bay gom theo HÃNG (mã hãng từ bảng flights). */
    @Query("select f.airlineCode, count(b), coalesce(sum(b.amount), 0) " +
            "from Booking b, com.dididi.booking.flight.domain.entity.Flight f " +
            "where f.id = b.targetId and b.type = com.dididi.booking.booking.domain.enums.BookingType.FLIGHT " +
            "and b.status = com.dididi.booking.booking.domain.enums.BookingStatus.CONFIRMED " +
            "and b.travelDate >= :start and b.travelDate < :end " +
            "group by f.airlineCode order by f.airlineCode")
    List<Object[]> aggregateFlightsByAirline(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * LƯỚI AN TOÀN "không rơi đơn nào": đơn CONFIRMED trong kỳ nhưng MỒ CÔI —
     * KS/chuyến bay nguồn không còn trong DB, hoặc thiếu mốc ngày dịch vụ.
     * Bình thường = 0; nếu > 0 thì phương trình dòng tiền hiện cảnh báo đỏ để admin truy.
     */
    @Query("select count(b), coalesce(sum(b.amount), 0) from Booking b " +
            "where b.status = com.dididi.booking.booking.domain.enums.BookingStatus.CONFIRMED " +
            "and ((b.type = com.dididi.booking.booking.domain.enums.BookingType.HOTEL " +
            "      and b.checkOut between :start and :end " +
            "      and not exists (select 1 from com.dididi.booking.hotel.domain.entity.Hotel h where h.id = b.targetId)) " +
            "  or (b.type = com.dididi.booking.booking.domain.enums.BookingType.FLIGHT " +
            "      and b.travelDate >= :startDt and b.travelDate < :endDt " +
            "      and not exists (select 1 from com.dididi.booking.flight.domain.entity.Flight f where f.id = b.targetId)))")
    List<Object[]> aggregateOrphans(@Param("start") LocalDate start, @Param("end") LocalDate end,
                                    @Param("startDt") LocalDateTime startDt, @Param("endDt") LocalDateTime endDt);

    /** Chi tiết đơn của 1 đối tác trong kỳ — cho file đối soát CSV. */
    @Query("select b from Booking b, com.dididi.booking.hotel.domain.entity.Hotel h " +
            "where h.id = b.targetId and b.type = com.dididi.booking.booking.domain.enums.BookingType.HOTEL " +
            "and b.status = com.dididi.booking.booking.domain.enums.BookingStatus.CONFIRMED " +
            "and h.vendorId is null and h.source = com.dididi.booking.hotel.domain.enums.HotelSource.CHANNEL " +
            "and b.checkOut between :start and :end order by b.checkOut, b.id")
    List<com.dididi.booking.booking.domain.entity.Booking> channelHotelBookings(
            @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("select b from Booking b, com.dididi.booking.flight.domain.entity.Flight f " +
            "where f.id = b.targetId and b.type = com.dididi.booking.booking.domain.enums.BookingType.FLIGHT " +
            "and b.status = com.dididi.booking.booking.domain.enums.BookingStatus.CONFIRMED " +
            "and f.airlineCode = :airline and b.travelDate >= :start and b.travelDate < :end " +
            "order by b.travelDate, b.id")
    List<com.dididi.booking.booking.domain.entity.Booking> airlineBookings(
            @Param("airline") String airline,
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** Tổng doanh thu CONFIRMED của kỳ (vế trái phương trình dòng tiền). */
    @Query("select count(b), coalesce(sum(b.amount), 0) from Booking b " +
            "where b.status = com.dididi.booking.booking.domain.enums.BookingStatus.CONFIRMED " +
            "and ((b.type = com.dididi.booking.booking.domain.enums.BookingType.HOTEL and b.checkOut between :start and :end) " +
            "  or (b.type = com.dididi.booking.booking.domain.enums.BookingType.FLIGHT and b.travelDate >= :startDt and b.travelDate < :endDt))")
    List<Object[]> aggregateTotal(@Param("start") LocalDate start, @Param("end") LocalDate end,
                                  @Param("startDt") LocalDateTime startDt, @Param("endDt") LocalDateTime endDt);

    default long[] countAndSum(List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) return new long[]{0, 0};
        Object[] r = rows.get(0);
        long count = ((Number) r[0]).longValue();
        BigDecimal sum = (BigDecimal) r[1];
        return new long[]{count, sum == null ? 0 : sum.longValue()};
    }
}
