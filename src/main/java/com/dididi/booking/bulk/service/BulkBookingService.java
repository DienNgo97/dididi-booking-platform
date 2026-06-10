package com.dididi.booking.bulk.service;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.service.BookingService;
import com.dididi.booking.bulk.api.dto.BulkLineResult;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.corporate.service.CorporateBookingService;
import com.dididi.booking.corporate.service.CorporatePaymentOutcome;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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
                                           List<String> guestNames, List<String> checkIns, List<String> checkOuts,
                                           List<String> roomCounts, boolean payByCompany) {
        List<BulkLineResult> results = new ArrayList<>();
        int n = guestNames == null ? 0 : guestNames.size();
        int no = 0;
        for (int idx = 0; idx < n; idx++) {
            String guest = trim(at(guestNames, idx));
            String ci = at(checkIns, idx), co = at(checkOuts, idx), rc = at(roomCounts, idx);
            if (guest.isEmpty() && isBlank(ci) && isBlank(co)) continue; // hang trong -> bo qua
            no++;
            if (guest.isEmpty()) { results.add(new BulkLineResult(no, guest, null, "FAILED", "Thiếu tên khách")); continue; }
            LocalDate cin, cout;
            try { cin = LocalDate.parse(ci.trim()); cout = LocalDate.parse(co.trim()); }
            catch (Exception e) { results.add(new BulkLineResult(no, guest, null, "FAILED", "Ngày không hợp lệ")); continue; }
            int rooms = 1;
            try { if (!isBlank(rc)) rooms = Math.max(1, Integer.parseInt(rc.trim())); } catch (Exception ignored) {}

            Booking b;
            try {
                b = bookingService.createHotelBooking(userId, hotelId, roomTypeId, roomName, guest, cin, cout, rooms);
            } catch (BusinessException ex) {
                results.add(new BulkLineResult(no, guest, null, "FAILED", ex.getMessage())); continue;
            } catch (Exception ex) {
                results.add(new BulkLineResult(no, guest, null, "FAILED", "Không tạo được đơn")); continue;
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
                // Don da tao nhung chua tra duoc (vd het han muc) -> de cho thanh toan, ghi chu loi.
                results.add(new BulkLineResult(no, guest, b.getPublicCode(), "PENDING_PAYMENT", ex.getMessage()));
            }
        }
        return results;
    }

    private static String at(List<String> l, int i) { return (l != null && i < l.size() && l.get(i) != null) ? l.get(i) : ""; }
    private static String trim(String s) { return s == null ? "" : s.trim(); }
    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
}
