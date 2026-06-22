package com.dididi.booking.admin.service;

import com.dididi.booking.admin.api.dto.AdminReportDto;
import com.dididi.booking.admin.api.dto.AdminReportPointDto;
import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.domain.enums.BookingType;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.Role;
import com.dididi.booking.identity.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Báo cáo cho khu admin (toàn sàn). Chỉ đọc dữ liệu sẵn có:
 *  - Doanh thu: các đơn CONFIRMED (lọc HOTEL/FLIGHT).
 *  - Người dùng/Vendor mới: User theo Role (CUSTOMER/VENDOR).
 * Không sửa repository nào — dùng findByStatusOrderByCreatedAtDesc + findAll.
 */
@Service
public class AdminReportService {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("dd/MM");

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final com.dididi.booking.commission.service.CommissionService commissionService;

    public AdminReportService(BookingRepository bookingRepository, UserRepository userRepository,
                              com.dididi.booking.commission.service.CommissionService commissionService) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.commissionService = commissionService;
    }

    public AdminReportDto report(String metricRaw, String granularityRaw) {
        String metric = normalizeMetric(metricRaw);
        String granularity = normalizeGranularity(granularityRaw);
        boolean revenue = metric.equals("HOTEL_REVENUE") || metric.equals("FLIGHT_REVENUE")
                || metric.equals("COMMISSION");

        List<Dated> data = gather(metric);

        List<AdminReportPointDto> series = new ArrayList<>();
        LocalDate today = LocalDate.now(ZONE);

        switch (granularity) {
            case "WEEK": {
                LocalDate firstMonday = today.with(DayOfWeek.MONDAY).minusWeeks(11);
                for (int i = 0; i < 12; i++) {
                    LocalDate start = firstMonday.plusWeeks(i);
                    LocalDate end = start.plusDays(6);
                    series.add(bucket("Tuần " + start.format(DM), data,
                            d -> !d.isBefore(start) && !d.isAfter(end), revenue));
                }
                break;
            }
            case "QUARTER": {
                int currentIdx = today.getYear() * 4 + ((today.getMonthValue() - 1) / 3);
                for (int i = 7; i >= 0; i--) {
                    int idx = currentIdx - i;
                    int year = idx / 4;
                    int quarter = idx % 4 + 1;
                    series.add(bucket("Q" + quarter + "/" + year, data,
                            d -> d.getYear() == year && ((d.getMonthValue() - 1) / 3 + 1) == quarter, revenue));
                }
                break;
            }
            case "YEAR": {
                TreeSet<Integer> years = new TreeSet<>();
                for (Dated d : data) {
                    if (d.date() != null) {
                        years.add(d.date().getYear());
                    }
                }
                if (years.isEmpty()) {
                    years.add(today.getYear());
                }
                for (int year : years) {
                    final int y = year;
                    series.add(bucket(String.valueOf(y), data, d -> d.getYear() == y, revenue));
                }
                break;
            }
            default: { // MONTH
                YearMonth firstMonth = YearMonth.from(today).minusMonths(11);
                for (int i = 0; i < 12; i++) {
                    final YearMonth ym = firstMonth.plusMonths(i);
                    series.add(bucket(label(ym), data, d -> YearMonth.from(d).equals(ym), revenue));
                }
            }
        }

        BigDecimal totalRevenue = BigDecimal.ZERO;
        long totalCount = 0;
        for (Dated d : data) {
            totalRevenue = totalRevenue.add(d.amount());
            totalCount++;
        }

        return new AdminReportDto(metric, granularity, revenue ? "REVENUE" : "COUNT", "VND",
                revenue ? totalRevenue : null, totalCount, series);
    }

    private List<Dated> gather(String metric) {
        List<Dated> data = new ArrayList<>();
        switch (metric) {
            case "FLIGHT_REVENUE":
                for (Booking b : bookingRepository.findByStatusOrderByCreatedAtDesc(BookingStatus.CONFIRMED)) {
                    if (b.getType() == BookingType.FLIGHT) {
                        data.add(new Dated(dateOf(b.getCreatedAt()), amount(b)));
                    }
                }
                break;
            case "NEW_USERS":
                for (User u : userRepository.findAll()) {
                    if (u.getRole() == Role.CUSTOMER) {
                        data.add(new Dated(dateOf(u.getCreatedAt()), BigDecimal.ZERO));
                    }
                }
                break;
            case "NEW_VENDORS":
                for (User u : userRepository.findAll()) {
                    if (u.getRole() == Role.VENDOR) {
                        data.add(new Dated(dateOf(u.getCreatedAt()), BigDecimal.ZERO));
                    }
                }
                break;
            case "COMMISSION":
                // Hoa hong theo ky: chi don co vendor (DIRECT); CHANNEL/ve may bay -> null, bo qua.
                for (Booking b : bookingRepository.findByStatusOrderByCreatedAtDesc(BookingStatus.CONFIRMED)) {
                    BigDecimal comm = commissionService.commissionForReport(b);
                    if (comm != null) {
                        data.add(new Dated(dateOf(b.getCreatedAt()), comm));
                    }
                }
                break;
            case "HOTEL_REVENUE":
            default:
                for (Booking b : bookingRepository.findByStatusOrderByCreatedAtDesc(BookingStatus.CONFIRMED)) {
                    if (b.getType() == BookingType.HOTEL) {
                        data.add(new Dated(dateOf(b.getCreatedAt()), amount(b)));
                    }
                }
        }
        return data;
    }

    private AdminReportPointDto bucket(String label, List<Dated> source, Predicate<LocalDate> inBucket, boolean revenue) {
        BigDecimal sum = BigDecimal.ZERO;
        long count = 0;
        for (Dated d : source) {
            if (d.date() != null && inBucket.test(d.date())) {
                sum = sum.add(d.amount());
                count++;
            }
        }
        return new AdminReportPointDto(label, revenue ? sum : null, count);
    }

    private static String normalizeMetric(String raw) {
        if (raw == null) {
            return "HOTEL_REVENUE";
        }
        String m = raw.trim().toUpperCase();
        switch (m) {
            case "HOTEL_REVENUE":
            case "FLIGHT_REVENUE":
            case "COMMISSION":
            case "NEW_USERS":
            case "NEW_VENDORS":
                return m;
            default:
                return "HOTEL_REVENUE";
        }
    }

    private static String normalizeGranularity(String raw) {
        if (raw == null) {
            return "MONTH";
        }
        String g = raw.trim().toUpperCase();
        switch (g) {
            case "WEEK":
            case "MONTH":
            case "QUARTER":
            case "YEAR":
                return g;
            default:
                return "MONTH";
        }
    }

    private static LocalDate dateOf(Instant instant) {
        return instant == null ? null : instant.atZone(ZONE).toLocalDate();
    }

    private static BigDecimal amount(Booking b) {
        return b.getAmount() == null ? BigDecimal.ZERO : b.getAmount();
    }

    private static String label(YearMonth ym) {
        return String.format("%02d/%d", ym.getMonthValue(), ym.getYear());
    }

    private record Dated(LocalDate date, BigDecimal amount) {
    }
}
