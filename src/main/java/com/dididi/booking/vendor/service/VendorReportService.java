package com.dididi.booking.vendor.service;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.domain.enums.BookingType;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.domain.entity.RoomType;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.hotel.repository.RoomTypeRepository;
import com.dididi.booking.review.domain.entity.Review;
import com.dididi.booking.review.service.ReviewService;
import com.dididi.booking.vendor.api.dto.GroupPreferenceDto;
import com.dididi.booking.vendor.api.dto.InventoryReportDto;
import com.dididi.booking.vendor.api.dto.RevenueReportDto;
import com.dididi.booking.vendor.api.dto.RoomInventoryStatDto;
import com.dididi.booking.vendor.api.dto.RoomTypeRevenueDto;
import com.dididi.booking.vendor.api.dto.SeriesPointDto;
import com.dididi.booking.vendor.api.dto.UpcomingCheckinDto;
import com.dididi.booking.vendor.api.dto.VendorDashboardDto;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;

/** Báo cáo cho vendor: doanh thu (theo kỳ), tồn kho, dashboard. Chỉ tính trên KS của vendor. */
@Service
public class VendorReportService {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final String VND = "VND";
    private static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("dd/MM");
    private static final List<String> TIER_ORDER = List.of("Thường", "Vàng", "Bạch kim", "Kim cương");
    private static final List<String> SEGMENT_ORDER = List.of("Khách lẻ", "Khách doanh nghiệp");

    private final HotelRepository hotelRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final BookingRepository bookingRepository;
    private final ReviewService reviewService;

    public VendorReportService(HotelRepository hotelRepository, RoomTypeRepository roomTypeRepository,
                               BookingRepository bookingRepository, ReviewService reviewService) {
        this.hotelRepository = hotelRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.bookingRepository = bookingRepository;
        this.reviewService = reviewService;
    }

    private Hotel myHotel(Long vendorUserId) {
        if (vendorUserId == null) {
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        return hotelRepository.findByVendorId(vendorUserId)
                .orElseThrow(() -> new BusinessException("NO_HOTEL",
                        "Tài khoản chưa gắn khách sạn nào", HttpStatus.NOT_FOUND));
    }

    // ============================ DOANH THU ============================
    public RevenueReportDto revenue(Long vendorUserId, String granularity) {
        Hotel hotel = myHotel(vendorUserId);
        Map<Long, String> rtName = roomTypeNames(hotel.getId());
        List<Booking> all = bookingRepository.findByTypeAndTargetIdAndStatus(
                BookingType.HOTEL, hotel.getId(), BookingStatus.CONFIRMED);

        String g = granularity == null ? "TOTAL" : granularity.trim().toUpperCase();
        LocalDate today = LocalDate.now();
        List<SeriesPointDto> series = new ArrayList<>();
        List<Booking> window;

        switch (g) {
            case "WEEK": {
                LocalDate from = today.with(DayOfWeek.MONDAY).minusWeeks(11);
                window = new ArrayList<>();
                for (Booking b : all) {
                    if (!dateOf(b).isBefore(from)) {
                        window.add(b);
                    }
                }
                for (int i = 0; i < 12; i++) {
                    LocalDate ws = from.plusWeeks(i);
                    LocalDate we = ws.plusDays(6);
                    series.add(bucket("Tuần " + ws.format(DM), window, d -> !d.isBefore(ws) && !d.isAfter(we)));
                }
                break;
            }
            case "MONTH": {
                YearMonth from = YearMonth.from(today).minusMonths(11);
                window = new ArrayList<>();
                for (Booking b : all) {
                    if (!YearMonth.from(dateOf(b)).isBefore(from)) {
                        window.add(b);
                    }
                }
                for (int i = 0; i < 12; i++) {
                    YearMonth m = from.plusMonths(i);
                    series.add(bucket(label(m), window, d -> YearMonth.from(d).equals(m)));
                }
                break;
            }
            case "YEAR": {
                window = all;
                TreeSet<Integer> years = new TreeSet<>();
                for (Booking b : all) {
                    years.add(dateOf(b).getYear());
                }
                if (years.isEmpty()) {
                    years.add(today.getYear());
                }
                for (int y : years) {
                    series.add(bucket(String.valueOf(y), all, d -> d.getYear() == y));
                }
                break;
            }
            default: {
                g = "TOTAL";
                window = all;
                YearMonth from = YearMonth.from(today).minusMonths(11);
                for (int i = 0; i < 12; i++) {
                    YearMonth m = from.plusMonths(i);
                    series.add(bucket(label(m), all, d -> YearMonth.from(d).equals(m)));
                }
            }
        }

        BigDecimal totalRev = BigDecimal.ZERO;
        long roomNights = 0;
        for (Booking b : window) {
            totalRev = totalRev.add(amt(b));
            roomNights += (long) nights(b) * Math.max(1, b.getQuantity());
        }
        long count = window.size();
        BigDecimal avg = count > 0
                ? totalRev.divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new RevenueReportDto(g, VND, totalRev, count, roomNights, avg, series,
                byRoomType(window, rtName), buildPref(window, rtName, this::tierGroup, TIER_ORDER),
                buildPref(window, rtName, this::segmentGroup, SEGMENT_ORDER));
    }

    private SeriesPointDto bucket(String label, List<Booking> src, java.util.function.Predicate<LocalDate> in) {
        BigDecimal rev = BigDecimal.ZERO;
        long cnt = 0;
        for (Booking b : src) {
            if (in.test(dateOf(b))) {
                rev = rev.add(amt(b));
                cnt++;
            }
        }
        return new SeriesPointDto(label, rev, cnt);
    }

    private List<RoomTypeRevenueDto> byRoomType(List<Booking> window, Map<Long, String> rtName) {
        Map<Long, BigDecimal> rev = new LinkedHashMap<>();
        Map<Long, Long> cnt = new LinkedHashMap<>();
        for (Booking b : window) {
            rev.merge(b.getRoomTypeId(), amt(b), BigDecimal::add);
            cnt.merge(b.getRoomTypeId(), 1L, Long::sum);
        }
        List<RoomTypeRevenueDto> out = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : rev.entrySet()) {
            out.add(new RoomTypeRevenueDto(e.getKey(), nameOf(rtName, e.getKey()), e.getValue(),
                    cnt.getOrDefault(e.getKey(), 0L)));
        }
        out.sort(Comparator.comparing(RoomTypeRevenueDto::revenue).reversed());
        return out;
    }

    private GroupPreferenceDto buildPref(List<Booking> window, Map<Long, String> rtName,
                                         Function<Booking, String> grouper, List<String> order) {
        List<Long> rtIds = new ArrayList<>(rtName.keySet());
        List<String> rtNames = new ArrayList<>();
        for (Long id : rtIds) {
            rtNames.add(rtName.get(id));
        }
        Map<String, long[]> counts = new LinkedHashMap<>();
        Map<String, Long> totals = new LinkedHashMap<>();
        for (Booking b : window) {
            String grp = grouper.apply(b);
            long[] arr = counts.computeIfAbsent(grp, k -> new long[rtIds.size()]);
            int idx = rtIds.indexOf(b.getRoomTypeId());
            if (idx >= 0) {
                arr[idx]++;
            }
            totals.merge(grp, 1L, Long::sum);
        }
        List<GroupPreferenceDto.GroupRow> rows = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (String grp : order) {
            if (counts.containsKey(grp)) {
                rows.add(row(grp, counts.get(grp), totals.get(grp)));
                seen.add(grp);
            }
        }
        for (String grp : counts.keySet()) {
            if (!seen.contains(grp)) {
                rows.add(row(grp, counts.get(grp), totals.get(grp)));
            }
        }
        return new GroupPreferenceDto(rtNames, rows);
    }

    private GroupPreferenceDto.GroupRow row(String grp, long[] arr, long total) {
        List<Long> list = new ArrayList<>();
        for (long c : arr) {
            list.add(c);
        }
        return new GroupPreferenceDto.GroupRow(grp, total, list);
    }

    // ============================ TỒN KHO ============================
    public InventoryReportDto inventory(Long vendorUserId, LocalDate from, LocalDate to) {
        Hotel hotel = myHotel(vendorUserId);
        if (from == null) {
            from = LocalDate.now();
        }
        if (to == null) {
            to = from.plusDays(29);
        }
        if (to.isBefore(from)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        List<RoomInventoryStatDto> rows = new ArrayList<>();
        for (RoomType rt : roomTypeRepository.findByHotelIdOrderByBasePrice(hotel.getId())) {
            List<Booking> bs = bookingRepository.findActiveForRoomType(
                    rt.getId(), from, to, List.of(BookingStatus.CONFIRMED));
            Map<LocalDate, Integer> perDay = bookedPerDay(bs, from, to);
            long booked = 0;
            for (int v : perDay.values()) {
                booked += v;
            }
            long capacity = (long) rt.getTotalRooms() * days;
            double occ = capacity > 0 ? round1(booked * 100.0 / capacity) : 0.0;
            int soldOut = 0;
            List<LocalDate> lowDays = new ArrayList<>();
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                int remaining = rt.getTotalRooms() - perDay.getOrDefault(d, 0);
                if (remaining <= 0) {
                    soldOut++;
                }
                if (remaining <= 2 && lowDays.size() < 15) {
                    lowDays.add(d);
                }
            }
            rows.add(new RoomInventoryStatDto(rt.getId(), rt.getName(), rt.getTotalRooms(),
                    booked, capacity, occ, soldOut, lowDays));
        }
        return new InventoryReportDto(from, to, days, VND, rows);
    }

    private Map<LocalDate, Integer> bookedPerDay(List<Booking> bs, LocalDate from, LocalDate to) {
        Map<LocalDate, Integer> map = new HashMap<>();
        LocalDate rangeEndExcl = to.plusDays(1);
        for (Booking b : bs) {
            LocalDate ci = b.getCheckIn() != null ? b.getCheckIn() : from;
            LocalDate coExcl = (b.getCheckOut() != null && b.getCheckOut().isAfter(ci))
                    ? b.getCheckOut() : ci.plusDays(1);
            LocalDate s = ci.isBefore(from) ? from : ci;
            LocalDate eExcl = coExcl.isAfter(rangeEndExcl) ? rangeEndExcl : coExcl;
            int q = Math.max(1, b.getQuantity());
            for (LocalDate d = s; d.isBefore(eExcl); d = d.plusDays(1)) {
                map.merge(d, q, Integer::sum);
            }
        }
        return map;
    }

    // ============================ DASHBOARD ============================
    public VendorDashboardDto dashboard(Long vendorUserId) {
        Hotel hotel = myHotel(vendorUserId);
        Map<Long, String> rtName = roomTypeNames(hotel.getId());
        List<Booking> confirmed = bookingRepository.findByTypeAndTargetIdAndStatus(
                BookingType.HOTEL, hotel.getId(), BookingStatus.CONFIRMED);
        LocalDate today = LocalDate.now();
        YearMonth thisM = YearMonth.from(today);
        YearMonth lastM = thisM.minusMonths(1);

        BigDecimal thisRev = BigDecimal.ZERO;
        BigDecimal lastRev = BigDecimal.ZERO;
        long thisCnt = 0;
        for (Booking b : confirmed) {
            YearMonth m = YearMonth.from(dateOf(b));
            if (m.equals(thisM)) {
                thisRev = thisRev.add(amt(b));
                thisCnt++;
            } else if (m.equals(lastM)) {
                lastRev = lastRev.add(amt(b));
            }
        }
        double changePct = lastRev.signum() > 0
                ? round1(thisRev.subtract(lastRev).multiply(BigDecimal.valueOf(100))
                        .divide(lastRev, 2, RoundingMode.HALF_UP).doubleValue())
                : (thisRev.signum() > 0 ? 100.0 : 0.0);
        BigDecimal avgOrder = thisCnt > 0
                ? thisRev.divide(BigDecimal.valueOf(thisCnt), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Cong suat 30 ngay toi
        LocalDate from = today;
        LocalDate to = today.plusDays(29);
        long bookedRN = 0;
        long capRN = 0;
        for (RoomType rt : roomTypeRepository.findByHotelIdOrderByBasePrice(hotel.getId())) {
            capRN += (long) rt.getTotalRooms() * 30;
            Map<LocalDate, Integer> perDay = bookedPerDay(
                    bookingRepository.findActiveForRoomType(rt.getId(), from, to, List.of(BookingStatus.CONFIRMED)),
                    from, to);
            for (int v : perDay.values()) {
                bookedRN += v;
            }
        }
        double occ = capRN > 0 ? round1(bookedRN * 100.0 / capRN) : 0.0;

        // Danh gia
        double avgRating = reviewService.averageRating(BookingType.HOTEL, hotel.getId());
        Page<Review> revs = reviewService.listForVendor(vendorUserId, 0, 1000);
        long reviewCount = revs.getTotalElements();
        long unanswered = revs.getContent().stream()
                .filter(r -> r.getVendorReply() == null || r.getVendorReply().isBlank())
                .count();

        // Don sap nhan phong 7 ngay toi
        LocalDate in7 = today.plusDays(7);
        List<UpcomingCheckinDto> upcoming = new ArrayList<>();
        confirmed.stream()
                .filter(b -> b.getCheckIn() != null && !b.getCheckIn().isBefore(today) && !b.getCheckIn().isAfter(in7))
                .sorted(Comparator.comparing(Booking::getCheckIn))
                .limit(12)
                .forEach(b -> upcoming.add(new UpcomingCheckinDto(b.getPublicCode(), b.getTitle(),
                        b.getCheckIn(), Math.max(1, b.getQuantity()), amt(b), nameOf(rtName, b.getRoomTypeId()))));

        // Top loai phong (toan thoi gian)
        List<RoomTypeRevenueDto> top = byRoomType(confirmed, rtName);
        if (top.size() > 5) {
            top = new ArrayList<>(top.subList(0, 5));
        }

        // Doanh thu 30 ngay gan nhat
        List<SeriesPointDto> last30 = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            last30.add(bucket(d.format(DM), confirmed, x -> x.equals(d)));
        }

        return new VendorDashboardDto(VND, thisRev, lastRev, changePct, thisCnt, avgOrder, occ,
                avgRating, reviewCount, unanswered, upcoming, top, last30);
    }

    // ============================ Helpers ============================
    private Map<Long, String> roomTypeNames(Long hotelId) {
        Map<Long, String> m = new LinkedHashMap<>();
        for (RoomType rt : roomTypeRepository.findByHotelIdOrderByBasePrice(hotelId)) {
            m.put(rt.getId(), rt.getName());
        }
        return m;
    }

    private static String nameOf(Map<Long, String> rtName, Long id) {
        if (id == null) {
            return "Khác";
        }
        return rtName.getOrDefault(id, "Phòng #" + id);
    }

    private static LocalDate dateOf(Booking b) {
        Instant c = b.getCreatedAt();
        return c != null ? LocalDate.ofInstant(c, ZONE) : LocalDate.now();
    }

    private static BigDecimal amt(Booking b) {
        return b.getAmount() == null ? BigDecimal.ZERO : b.getAmount();
    }

    private static int nights(Booking b) {
        if (b.getCheckIn() != null && b.getCheckOut() != null && b.getCheckOut().isAfter(b.getCheckIn())) {
            return (int) ChronoUnit.DAYS.between(b.getCheckIn(), b.getCheckOut());
        }
        return 1;
    }

    private String tierGroup(Booking b) {
        String t = b.getTier();
        if (t == null || t.isBlank()) {
            return "Thường";
        }
        switch (t.toUpperCase()) {
            case "GOLD": return "Vàng";
            case "PLATINUM": return "Bạch kim";
            case "DIAMOND": return "Kim cương";
            default: return "Thường";
        }
    }

    private String segmentGroup(Booking b) {
        return b.getCompanyId() != null ? "Khách doanh nghiệp" : "Khách lẻ";
    }

    private static String label(YearMonth m) {
        return String.format("%02d/%d", m.getMonthValue(), m.getYear());
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
