package com.dididi.booking.bulk.service;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.service.BookingService;
import com.dididi.booking.bulk.api.dto.BulkLineResult;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.corporate.service.CorporateBookingService;
import com.dididi.booking.corporate.service.CorporatePaymentOutcome;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Dat theo nhom: tao nhieu don KS cung khach san/loai phong trong 1 lan.
 * Moi dong xu ly doc lap (best-effort). Neu chon tra bang ngan sach cong ty,
 * tai dung CorporateBookingService.payWithCompanyBudget (tu dong duyet/tru han muc).
 */
@Service
public class BulkBookingService {

    private final BookingService bookingService;
    private final CorporateBookingService corporateBookingService;

    public BulkBookingService(BookingService bookingService, CorporateBookingService corporateBookingService) {
        this.bookingService = bookingService;
        this.corporateBookingService = corporateBookingService;
    }

    public List<BulkLineResult> createBulk(Long userId, Long hotelId, Long roomTypeId, String roomName,
                                           String stay,
                                           List<String> guestNames, List<String> checkIns, List<String> checkOuts,
                                           List<String> dayDates, List<String> timeIns, List<String> timeOuts,
                                           List<String> roomCounts, boolean payByCompany) {
        boolean dayUse = "day".equalsIgnoreCase(stay);
        List<BulkLineResult> results = new ArrayList<>();
        int n = guestNames == null ? 0 : guestNames.size();
        int no = 0;
        for (int idx = 0; idx < n; idx++) {
            String guest = trim(at(guestNames, idx));
            int rooms = 1;
            try { String rc = at(roomCounts, idx); if (!isBlank(rc)) rooms = Math.max(1, Integer.parseInt(rc.trim())); }
            catch (Exception ignored) {}

            // Không nhập tên khách -> bỏ qua dòng này, xem như không nhập (KHÔNG báo lỗi).
            if (guest.isEmpty()) continue;

            Booking b;
            if (dayUse) {
                // Đặt theo giờ (trong ngày): ngày + giờ nhận + giờ trả; giá nửa/cả ngày do BookingService tính.
                // Ngày/giờ không hợp lệ (vd để trống ngày) -> bỏ qua dòng, xem như không nhập.
                String d = at(dayDates, idx), ti = at(timeIns, idx), to = at(timeOuts, idx);
                LocalDate date; LocalTime tin, tout;
                try { date = LocalDate.parse(d.trim()); tin = LocalTime.parse(ti.trim()); tout = LocalTime.parse(to.trim()); }
                catch (Exception e) { results.add(new BulkLineResult(idx + 1, guest, null, "SKIPPED", "Ngày/giờ không hợp lệ — bỏ qua dòng")); continue; }
                no++;
                try {
                    b = bookingService.createDayUseHotelBooking(userId, hotelId, roomTypeId, roomName, guest, date, tin, tout, rooms);
                } catch (BusinessException ex) { results.add(fail(no, guest, ex.getMessage())); continue; }
                catch (Exception ex) { results.add(fail(no, guest, "Không tạo được đơn")); continue; }
            } else {
                // Đặt theo ngày (qua đêm). Ngày không hợp lệ (vd để trống) -> bỏ qua dòng, xem như không nhập.
                String ci = at(checkIns, idx), co = at(checkOuts, idx);
                LocalDate cin, cout;
                try { cin = LocalDate.parse(ci.trim()); cout = LocalDate.parse(co.trim()); }
                catch (Exception e) { results.add(new BulkLineResult(idx + 1, guest, null, "SKIPPED", "Ngày không hợp lệ — bỏ qua dòng")); continue; }
                no++;
                try {
                    b = bookingService.createHotelBooking(userId, hotelId, roomTypeId, roomName, guest, cin, cout, rooms);
                } catch (BusinessException ex) { results.add(fail(no, guest, ex.getMessage())); continue; }
                catch (Exception ex) { results.add(fail(no, guest, "Không tạo được đơn")); continue; }
            }

            if (!payByCompany) {
                results.add(new BulkLineResult(no, guest, b.getPublicCode(), "PENDING_PAYMENT", null));
                continue;
            }
            try {
                CorporatePaymentOutcome out = corporateBookingService.payWithCompanyBudget(b.getPublicCode(), userId);
                String st = (out == CorporatePaymentOutcome.CONFIRMED) ? "CONFIRMED" : "PENDING_APPROVAL";
                results.add(new BulkLineResult(no, guest, b.getPublicCode(), st, null));
            } catch (BusinessException ex) {
                // Đơn đã tạo nhưng chưa trả được (vd hết hạn mức) -> để chờ thanh toán, ghi chú lỗi.
                results.add(new BulkLineResult(no, guest, b.getPublicCode(), "PENDING_PAYMENT", ex.getMessage()));
            }
        }
        return results;
    }

    private static BulkLineResult fail(int no, String guest, String msg) {
        return new BulkLineResult(no, guest, null, "FAILED", msg);
    }

    private static String at(List<String> l, int i) { return (l != null && i < l.size() && l.get(i) != null) ? l.get(i) : ""; }
    private static String trim(String s) { return s == null ? "" : s.trim(); }
    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
}
