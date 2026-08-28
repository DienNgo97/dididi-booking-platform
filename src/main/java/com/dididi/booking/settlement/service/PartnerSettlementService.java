package com.dididi.booking.settlement.service;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.commission.service.CommissionService;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.common.i18n.I18nSupport;
import com.dididi.booking.flight.domain.Airlines;
import com.dididi.booking.settlement.domain.PartnerSettlement;
import com.dididi.booking.settlement.repository.PartnerSettlementRepository;
import com.dididi.booking.wallet.service.VendorWalletService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Đối soát công nợ B2B với đối tác API theo kỳ tháng (ST2, chốt thiết kế với Jay 27/08/2026).
 *
 * NGUYÊN TẮC "KHÔNG LỖ HỔNG DOANH THU": mọi đồng doanh thu CONFIRMED của kỳ rơi vào ĐÚNG MỘT ô:
 *   Tổng kỳ = Ví vendor (KS DIRECT có vendor) + Công nợ HOTEL_PMS (KS CHANNEL)
 *           + Công nợ từng hãng bay + Tự doanh nền tảng (KS DIRECT không vendor) + Mồ côi (phải = 0).
 * Phương trình này được TÍNH VÀ KIỂM mỗi lần xem kỳ — lệch đồng nào UI hiện đỏ đồng đó.
 *
 * Chỉ CHỐT được kỳ đã kết thúc + {@link VendorWalletService#COMPLAINT_WINDOW_DAYS} ngày:
 * lúc đó mọi đơn trong kỳ đã hết cửa hoàn tiền (guard trong RefundService) → số chốt là bất biến.
 */
@Service
public class PartnerSettlementService {

    public static final String HOTEL_PMS = "HOTEL_PMS";

    private final PartnerSettlementRepository repository;
    private final CommissionService commissionService;

    /** Phần nền tảng giữ lại trên mỗi vé máy bay (mô hình hoa hồng đại lý). */
    @Value("${app.settlement.flight-commission-rate:0.05}")
    private BigDecimal flightRate;

    public PartnerSettlementService(PartnerSettlementRepository repository,
                                    CommissionService commissionService) {
        this.repository = repository;
        this.commissionService = commissionService;
    }

    // ================= VIEW MODEL =================

    public record Row(String partnerCode, String partnerName, String kind,
                      long bookingCount, BigDecimal gross, BigDecimal commissionRate,
                      BigDecimal commissionAmount, BigDecimal netPayable,
                      String status, boolean closable, String paymentRef) {}

    public record Overview(String periodYm, long totalCount, BigDecimal totalGross,
                           BigDecimal vendorWalletGross, BigDecimal partnerGross,
                           BigDecimal platformGross, BigDecimal orphanGross,
                           long orphanCount, boolean balanced, boolean periodClosable,
                           // P1-2: đơn CONFIRMED không có mốc ngày dịch vụ -> không thuộc kỳ nào.
                           // Không lọc theo kỳ (chúng vô hình với mọi kỳ) nên hiện ở mọi kỳ tới khi vá xong.
                           long undatableCount, BigDecimal undatableGross) {}

    public record PeriodView(Overview overview, List<Row> rows) {}

    // ================= TÍNH KỲ =================

    @Transactional(readOnly = true)
    public PeriodView view(String periodYm) {
        YearMonth ym = parse(periodYm);
        LocalDate start = ym.atDay(1), end = ym.atEndOfMonth();
        LocalDateTime startDt = start.atStartOfDay(), endDt = end.plusDays(1).atStartOfDay();
        boolean closable = periodClosable(ym);

        long[] vendor = parse2(repository.aggregateVendorHotels(start, end));
        long[] platform = parse2(repository.aggregatePlatformHotels(start, end));
        long[] channel = parse2(repository.aggregateChannelHotels(start, end));
        long[] orphan = parse2(repository.aggregateOrphans(start, end, startDt, endDt));
        long[] total = parse2(repository.aggregateTotal(start, end, startDt, endDt));

        Map<String, PartnerSettlement> persisted = new java.util.HashMap<>();
        for (PartnerSettlement s : repository.findByPeriodYm(periodYm)) {
            persisted.put(s.getPartnerCode(), s);
        }

        List<Row> rows = new ArrayList<>();
        BigDecimal partnerGross = BigDecimal.ZERO;

        // --- HOTEL_PMS (KS CHANNEL) ---
        rows.add(buildRow(persisted.get(HOTEL_PMS), HOTEL_PMS, "Hotel PMS (khách sạn đồng bộ)", "PARTNER",
                channel[0], vnd(channel[1]), commissionService.getDefaultRate(), closable));
        partnerGross = partnerGross.add(vnd(channel[1]));

        // --- Từng hãng bay ---
        for (Object[] r : repository.aggregateFlightsByAirline(startDt, endDt)) {
            String code = r[0] == null ? "N/A" : (String) r[0];
            long count = ((Number) r[1]).longValue();
            BigDecimal gross = r[2] == null ? BigDecimal.ZERO : (BigDecimal) r[2];
            rows.add(buildRow(persisted.get(code), code, Airlines.nameOf(code), "PARTNER",
                    count, gross, flightRate, closable));
            partnerGross = partnerGross.add(gross);
        }

        // --- Tự doanh nền tảng (thông tin, không phải trả ai) ---
        rows.add(new Row("PLATFORM", "Khách sạn tự doanh nền tảng", "PLATFORM",
                platform[0], vnd(platform[1]), null, null, BigDecimal.ZERO, "N/A", false, null));
        // --- Ví vendor (đã có kênh riêng — hiện để phương trình đủ vế) ---
        rows.add(new Row("VENDOR_WALLET", "Ví vendor (KS đối tác trực tiếp)", "WALLET",
                vendor[0], vnd(vendor[1]), null, null, BigDecimal.ZERO, "N/A", false, null));

        long[] undatable = parse2(repository.aggregateUndatable());

        BigDecimal sumParts = vnd(vendor[1]).add(partnerGross).add(vnd(platform[1])).add(vnd(orphan[1]));
        // Phương trình chỉ được coi là CÂN khi vừa khớp số, vừa không có đơn nào rơi ra ngoài lưới:
        // mồ côi (mất KS/chuyến bay nguồn) = 0 VÀ không quy được kỳ (thiếu mốc ngày) = 0.
        boolean balanced = sumParts.compareTo(vnd(total[1])) == 0 && orphan[0] == 0 && undatable[0] == 0;

        Overview ov = new Overview(periodYm, total[0], vnd(total[1]), vnd(vendor[1]), partnerGross,
                vnd(platform[1]), vnd(orphan[1]), orphan[0], balanced, closable,
                undatable[0], vnd(undatable[1]));
        return new PeriodView(ov, rows);
    }

    // ================= CHỐT KỲ / ĐÃ TRẢ =================

    @Transactional
    public PartnerSettlement closePeriod(String partnerCode, String periodYm, Long adminId) {
        YearMonth ym = parse(periodYm);
        if (!periodClosable(ym)) {
            throw new BusinessException("PERIOD_NOT_CLOSABLE",
                    I18nSupport.msg("err.PERIOD_NOT_CLOSABLE",
                            "Chỉ chốt được kỳ đã kết thúc {0} ngày (hết cửa sổ khiếu nại — số liệu mới bất biến).",
                            String.valueOf(VendorWalletService.COMPLAINT_WINDOW_DAYS)),
                    HttpStatus.CONFLICT);
        }
        if (repository.findByPartnerCodeAndPeriodYm(partnerCode, periodYm).isPresent()) {
            throw new BusinessException("ALREADY_CLOSED",
                    I18nSupport.msg("err.ALREADY_CLOSED", "Kỳ này của đối tác đã được chốt."),
                    HttpStatus.CONFLICT);
        }
        // Tìm dòng live tương ứng — chỉ đối tác THẬT (PARTNER) mới chốt được
        Row live = view(periodYm).rows().stream()
                .filter(r -> r.partnerCode().equals(partnerCode) && "PARTNER".equals(r.kind()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("PARTNER_NOT_FOUND",
                        "Không có số liệu đối tác này trong kỳ", HttpStatus.NOT_FOUND));

        PartnerSettlement s = new PartnerSettlement();
        s.setPartnerCode(live.partnerCode());
        s.setPartnerName(live.partnerName());
        s.setPeriodYm(periodYm);
        s.setBookingCount(live.bookingCount());
        s.setGross(live.gross());
        s.setCommissionRate(live.commissionRate());
        s.setCommissionAmount(live.commissionAmount());
        s.setNetPayable(live.netPayable());
        s.setStatus(PartnerSettlement.Status.CLOSED);
        s.setClosedBy(adminId);
        return repository.save(s);   // unique (partner, period) chặn race chốt trùng
    }

    @Transactional
    public PartnerSettlement markPaid(String partnerCode, String periodYm, Long adminId, String paymentRef) {
        if (paymentRef == null || paymentRef.isBlank()) {
            throw new BusinessException("PAYMENT_REF_REQUIRED",
                    I18nSupport.msg("err.PAYMENT_REF_REQUIRED", "Vui lòng nhập mã UNC/chuyển khoản."),
                    HttpStatus.BAD_REQUEST);
        }
        PartnerSettlement s = repository.findByPartnerCodeAndPeriodYm(partnerCode, periodYm)
                .orElseThrow(() -> new BusinessException("NOT_CLOSED",
                        I18nSupport.msg("err.NOT_CLOSED", "Kỳ chưa được chốt — chốt kỳ trước khi ghi nhận thanh toán."),
                        HttpStatus.CONFLICT));
        if (s.getStatus() == PartnerSettlement.Status.PAID) {
            throw new BusinessException("ALREADY_PAID",
                    I18nSupport.msg("err.ALREADY_PAID", "Kỳ này đã được ghi nhận thanh toán."),
                    HttpStatus.CONFLICT);
        }
        s.setStatus(PartnerSettlement.Status.PAID);
        s.setPaidAt(java.time.Instant.now());
        s.setPaidBy(adminId);
        s.setPaymentRef(paymentRef.trim());
        return repository.save(s);
    }

    // ================= CSV ĐỐI SOÁT =================

    /** File đối soát chi tiết từng đơn (UTF-8 BOM để Excel mở tiếng Việt không vỡ). */
    @Transactional(readOnly = true)
    public String exportCsv(String partnerCode, String periodYm) {
        YearMonth ym = parse(periodYm);
        LocalDate start = ym.atDay(1), end = ym.atEndOfMonth();
        BigDecimal rate;
        List<Booking> bookings;
        if (HOTEL_PMS.equals(partnerCode)) {
            rate = commissionService.getDefaultRate();
            bookings = repository.channelHotelBookings(start, end);
        } else {
            rate = flightRate;
            bookings = repository.airlineBookings(partnerCode, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        }
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("Mã đơn,Ngày dịch vụ,Nội dung,Doanh thu gộp,Hoa hồng nền tảng,Phải trả đối tác\n");
        BigDecimal tGross = BigDecimal.ZERO, tComm = BigDecimal.ZERO;
        for (Booking b : bookings) {
            BigDecimal gross = b.getAmount() == null ? BigDecimal.ZERO : b.getAmount();
            BigDecimal comm = gross.multiply(rate).setScale(0, RoundingMode.HALF_UP);
            String serviceDate = b.getCheckOut() != null ? b.getCheckOut().toString()
                    : (b.getTravelDate() != null ? b.getTravelDate().toLocalDate().toString() : "");
            sb.append(csv(b.getPublicCode())).append(',').append(serviceDate).append(',')
              .append(csv(b.getTitle())).append(',').append(gross.toBigInteger()).append(',')
              .append(comm.toBigInteger()).append(',').append(gross.subtract(comm).toBigInteger()).append('\n');
            tGross = tGross.add(gross);
            tComm = tComm.add(comm);
        }
        sb.append("TỔNG,,").append(csv("Kỳ " + periodYm + " — " + bookings.size() + " đơn")).append(',')
          .append(tGross.toBigInteger()).append(',').append(tComm.toBigInteger()).append(',')
          .append(tGross.subtract(tComm).toBigInteger()).append('\n');
        return sb.toString();
    }

    // ================= helpers =================

    private Row buildRow(PartnerSettlement persisted, String code, String name, String kind,
                         long liveCount, BigDecimal liveGross, BigDecimal liveRate, boolean closable) {
        if (persisted != null) {   // đã chốt -> số BẤT BIẾN lấy từ bản ghi
            return new Row(code, persisted.getPartnerName(), kind, persisted.getBookingCount(),
                    persisted.getGross(), persisted.getCommissionRate(), persisted.getCommissionAmount(),
                    persisted.getNetPayable(), persisted.getStatus().name(), false, persisted.getPaymentRef());
        }
        BigDecimal comm = liveGross.multiply(liveRate).setScale(0, RoundingMode.HALF_UP);
        return new Row(code, name, kind, liveCount, liveGross, liveRate, comm,
                liveGross.subtract(comm), "OPEN", closable && liveCount > 0, null);
    }

    /** Kỳ chốt được khi đã qua hết tháng + cửa sổ khiếu nại (mọi đơn trong kỳ hết đường hoàn). */
    private boolean periodClosable(YearMonth ym) {
        return LocalDate.now().isAfter(ym.atEndOfMonth().plusDays(VendorWalletService.COMPLAINT_WINDOW_DAYS));
    }

    private static YearMonth parse(String periodYm) {
        try {
            return YearMonth.parse(periodYm);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BusinessException("INVALID_PERIOD", "Kỳ không hợp lệ (định dạng yyyy-MM)", HttpStatus.BAD_REQUEST);
        }
    }

    private static long[] parse2(List<Object[]> rows) {
        if (rows == null || rows.isEmpty() || rows.get(0)[0] == null) return new long[]{0, 0};
        Object[] r = rows.get(0);
        long count = ((Number) r[0]).longValue();
        long sum = r[1] == null ? 0 : ((BigDecimal) r[1]).longValue();
        return new long[]{count, sum};
    }

    private static BigDecimal vnd(long v) { return BigDecimal.valueOf(v); }

    private static String csv(String s) {
        if (s == null) return "";
        return (s.contains(",") || s.contains("\"") || s.contains("\n"))
                ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    public static Optional<String> partnerNameOf(String code) {
        return Optional.of(HOTEL_PMS.equals(code) ? "Hotel PMS (khách sạn đồng bộ)" : Airlines.nameOf(code));
    }
}
