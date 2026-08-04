package com.dididi.booking.group.api.controller;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.group.domain.entity.GroupBooking;
import com.dididi.booking.group.service.GroupBookingService;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.domain.entity.RoomType;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.invoice.service.InvoiceService;
import com.dididi.booking.payment.service.PaymentService;
import com.dididi.booking.payment.vnpay.VnPayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Nhóm du lịch / đặt theo nhóm cho khách (JWT). Dùng lại GroupBookingService như bản web /groups.
 */
@Tag(name = "Groups (khách)")
@RestController
@RequestMapping("/api/v1/groups")
public class GroupApiController {

    private final GroupBookingService groupService;
    private final HotelRepository hotelRepository;
    private final UserRepository userRepository;
    private final InvoiceService invoiceService;
    private final PaymentService paymentService;
    private final VnPayService vnPayService;

    public GroupApiController(GroupBookingService groupService, HotelRepository hotelRepository,
                             UserRepository userRepository, InvoiceService invoiceService,
                             PaymentService paymentService, VnPayService vnPayService) {
        this.groupService = groupService;
        this.hotelRepository = hotelRepository;
        this.userRepository = userRepository;
        this.invoiceService = invoiceService;
        this.paymentService = paymentService;
        this.vnPayService = vnPayService;
    }

    private Long uid(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        return Long.valueOf(auth.getName());
    }

    private String hotelName(Long hotelId) {
        return hotelRepository.findById(hotelId).map(Hotel::getName).orElse("#" + hotelId);
    }

    @Operation(summary = "Tạo nhóm đặt phòng")
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body, Authentication auth) {
        Long userId = uid(auth);
        Long hotelId = Long.valueOf(body.get("hotelId").toString());
        Long roomTypeId = Long.valueOf(body.get("roomTypeId").toString());
        String roomName = body.get("roomName") == null ? null : body.get("roomName").toString();
        LocalDate checkIn = LocalDate.parse(body.get("checkIn").toString());
        LocalDate checkOut = LocalDate.parse(body.get("checkOut").toString());
        String title = body.get("title") == null ? null : body.get("title").toString();
        GroupBooking g = groupService.createGroup(userId, hotelId, roomTypeId, roomName, checkIn, checkOut, title);
        return ApiResponse.ok(summary(g, userId), "Đã tạo nhóm");
    }

    @Operation(summary = "Nhóm của tôi (tổ chức / tham gia)")
    @GetMapping("/me")
    public ApiResponse<List<Map<String, Object>>> myGroups(Authentication auth) {
        Long userId = uid(auth);
        List<Map<String, Object>> out = new ArrayList<>();
        for (GroupBooking g : groupService.myGroups(userId)) {
            out.add(summary(g, userId));
        }
        return ApiResponse.ok(out);
    }

    @Operation(summary = "Bảng điều khiển nhóm theo token")
    @GetMapping("/{token}")
    public ApiResponse<Map<String, Object>> dashboard(@PathVariable String token, Authentication auth) {
        Long userId = uid(auth);
        GroupBooking g = groupService.getByToken(token);

        List<Map<String, Object>> members = new ArrayList<>();
        BigDecimal totalAll = BigDecimal.ZERO, totalPaid = BigDecimal.ZERO;
        int paidCount = 0;
        for (Booking b : groupService.members(g.getId())) {
            BookingStatus s = b.getStatus();
            if (s != BookingStatus.PENDING_PAYMENT && s != BookingStatus.CONFIRMED) continue;
            String name = userRepository.findById(b.getUserId()).map(User::getFullName).orElse("Khách");
            boolean paid = s == BookingStatus.CONFIRMED;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("userId", b.getUserId());
            m.put("rooms", b.getQuantity());
            m.put("amount", b.getAmount());
            m.put("status", s.name());
            m.put("bookingCode", b.getPublicCode());
            m.put("mine", b.getUserId().equals(userId));
            members.add(m);
            if (b.getAmount() != null) {
                totalAll = totalAll.add(b.getAmount());
                if (paid) totalPaid = totalPaid.add(b.getAmount());
            }
            if (paid) paidCount++;
        }

        List<Map<String, Object>> roomTypes = new ArrayList<>();
        for (RoomType rt : groupService.roomTypesFor(g.getHotelId())) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", rt.getId());
            r.put("name", rt.getName());
            r.put("basePrice", rt.getBasePrice());
            roomTypes.add(r);
        }

        Map<String, Object> out = summary(g, userId);
        out.put("members", members);
        out.put("memberCount", members.size());
        out.put("paidCount", paidCount);
        out.put("totalAll", totalAll);
        out.put("totalPaid", totalPaid);
        out.put("roomTypes", roomTypes);
        boolean closed = "CLOSED".equals(g.getStatus());
        out.put("closed", closed);
        out.put("joinable", !closed && !g.isEnded());
        return ApiResponse.ok(out);
    }

    @Operation(summary = "Tham gia nhóm: thêm phòng của tôi (PENDING_PAYMENT)")
    @PostMapping("/{token}/join")
    public ApiResponse<Map<String, Object>> join(@PathVariable String token, @RequestBody(required = false) Map<String, Object> body,
                                                 Authentication auth) {
        Long userId = uid(auth);
        GroupBooking g = groupService.getByToken(token);
        Map<String, Object> b = body == null ? Map.of() : body;
        Long roomTypeId = b.get("roomTypeId") == null ? null : Long.valueOf(b.get("roomTypeId").toString());
        int rooms = b.get("rooms") == null ? 1 : Integer.parseInt(b.get("rooms").toString());
        String name = b.get("guestName") == null
                ? userRepository.findById(userId).map(User::getFullName).orElse("Khách")
                : b.get("guestName").toString();
        Booking booking = groupService.addMyRoom(g, userId, name, roomTypeId, g.getCheckIn(), g.getCheckOut(), rooms);
        return ApiResponse.ok(Map.of("bookingCode", booking.getPublicCode(), "amount", booking.getAmount()),
                "Đã thêm phòng vào nhóm");
    }

    @Operation(summary = "Thanh toán cho CẢ NHÓM trong 1 giao dịch VNPay (chủ nhóm)")
    @PostMapping("/{token}/pay-group")
    public ApiResponse<Map<String, Object>> payGroup(@PathVariable String token, Authentication auth) {
        Long userId = uid(auth);
        GroupBooking g = groupService.getByToken(token);
        if (!userId.equals(g.getOrganizerUserId())) {
            throw new BusinessException("FORBIDDEN", "Chỉ chủ nhóm mới thanh toán cho cả nhóm", HttpStatus.FORBIDDEN);
        }
        List<Booking> chosen = groupService.beginGroupPayment(g.getId());
        Booking lead = null;
        BigDecimal total = BigDecimal.ZERO;
        for (Booking b : chosen) {
            if (lead == null) lead = b;
            if (b.getAmount() != null) total = total.add(b.getAmount());
        }
        if (lead == null) {
            throw new BusinessException("NOTHING_TO_PAY", "Không có phòng nào cần thanh toán", HttpStatus.BAD_REQUEST);
        }
        // Cùng định dạng txnRef "GRP{groupId}_..." với web để handler vnpay-return xác nhận đúng nhóm.
        String txnRef = "GRP" + g.getId() + "_" + System.currentTimeMillis();
        paymentService.initiateVnpayWithAmount(lead, total, txnRef);
        String url = vnPayService.createPaymentUrl(total, txnRef, "Thanh toan nhom #" + g.getId(), null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("payUrl", url);
        out.put("total", total);
        out.put("rooms", chosen.size());
        return ApiResponse.ok(out, "Đã tạo liên kết thanh toán nhóm");
    }

    @Operation(summary = "Đóng nhóm (chủ nhóm)")
    @PostMapping("/{token}/close")
    public ApiResponse<Void> close(@PathVariable String token, Authentication auth) {
        groupService.closeGroup(token, uid(auth));
        return ApiResponse.ok(null, "Đã đóng nhóm");
    }

    @Operation(summary = "Mở lại nhóm (chủ nhóm)")
    @PostMapping("/{token}/reopen")
    public ApiResponse<Void> reopen(@PathVariable String token, Authentication auth) {
        groupService.reopenGroup(token, uid(auth));
        return ApiResponse.ok(null, "Đã mở lại nhóm");
    }

    @Operation(summary = "Xoá thành viên khỏi nhóm (chủ nhóm)")
    @PostMapping("/{token}/members/{memberUserId}/remove")
    public ApiResponse<Void> removeMember(@PathVariable String token, @PathVariable Long memberUserId,
                                          Authentication auth) {
        groupService.removeMember(token, memberUserId, uid(auth));
        return ApiResponse.ok(null, "Đã xoá thành viên khỏi nhóm");
    }

    @Operation(summary = "Xoá một phòng khỏi nhóm (chủ nhóm hoặc chủ phòng, chưa thanh toán)")
    @PostMapping("/{token}/rooms/{bookingCode}/delete")
    public ApiResponse<Void> deleteRoom(@PathVariable String token, @PathVariable String bookingCode,
                                        Authentication auth) {
        groupService.removeRoom(token, bookingCode, uid(auth));
        return ApiResponse.ok(null, "Đã xoá phòng");
    }

    @Operation(summary = "Sửa nhóm (tiêu đề / chia đều) — chủ nhóm")
    @PostMapping("/{token}/edit")
    public ApiResponse<Map<String, Object>> edit(@PathVariable String token, @RequestBody Map<String, Object> body,
                                                 Authentication auth) {
        Long userId = uid(auth);
        GroupBooking cur = groupService.getByToken(token);
        String title = body.get("title") == null ? null : body.get("title").toString();
        Long roomTypeId = body.get("roomTypeId") == null ? null : Long.valueOf(body.get("roomTypeId").toString());
        boolean splitEven = body.get("splitEven") != null && Boolean.parseBoolean(body.get("splitEven").toString());
        GroupBooking g = groupService.updateGroup(token, userId, title, roomTypeId, cur.getDeadline(), splitEven);
        return ApiResponse.ok(summary(g, userId), "Đã cập nhật nhóm");
    }

    @Operation(summary = "Kết thúc chuyến (mở khoá chia tiền) — chủ nhóm")
    @PostMapping("/{token}/end")
    public ApiResponse<Void> endTrip(@PathVariable String token, Authentication auth) {
        groupService.endTrip(token, uid(auth));
        return ApiResponse.ok(null, "Đã kết thúc chuyến");
    }

    @Operation(summary = "Mở lại chuyến — chủ nhóm")
    @PostMapping("/{token}/reopen-trip")
    public ApiResponse<Void> reopenTrip(@PathVariable String token, Authentication auth) {
        groupService.reopenTrip(token, uid(auth));
        return ApiResponse.ok(null, "Đã mở lại chuyến");
    }

    @Operation(summary = "Hoá đơn chia tiền nhóm (PDF) — chủ nhóm, sau khi kết thúc chuyến")
    @GetMapping("/{token}/settlement")
    public ResponseEntity<byte[]> settlement(@PathVariable String token, Authentication auth) {
        Long userId = uid(auth);
        GroupBooking g = groupService.getByToken(token);
        if (!userId.equals(g.getOrganizerUserId())) {
            throw new BusinessException("FORBIDDEN", "Chỉ chủ nhóm mới xuất được hoá đơn chia tiền", HttpStatus.FORBIDDEN);
        }
        if (!g.isEnded()) {
            throw new BusinessException("NOT_ENDED", "Cần kết thúc chuyến trước khi xuất hoá đơn chia tiền",
                    HttpStatus.BAD_REQUEST);
        }
        List<Booking> confirmed = new ArrayList<>();
        for (Booking b : groupService.members(g.getId())) {
            if (b.getStatus() == BookingStatus.CONFIRMED) confirmed.add(b);
        }
        Hotel hotel = hotelRepository.findById(g.getHotelId()).orElse(null);
        User organizer = userRepository.findById(g.getOrganizerUserId()).orElse(null);
        byte[] pdf = invoiceService.generateGroupSettlement(g, confirmed,
                hotel != null ? hotel.getName() : "#" + g.getHotelId(),
                hotel != null ? hotel.getAddress() : "", organizer);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"chia-tien-" + token + ".pdf\"")
                .body(pdf);
    }

    private Map<String, Object> summary(GroupBooking g, Long userId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", g.getToken());
        m.put("title", g.getTitle());
        m.put("hotelId", g.getHotelId());
        m.put("hotelName", hotelName(g.getHotelId()));
        m.put("checkIn", g.getCheckIn() == null ? null : g.getCheckIn().toString());
        m.put("checkOut", g.getCheckOut() == null ? null : g.getCheckOut().toString());
        m.put("organizer", userId.equals(g.getOrganizerUserId()));
        m.put("status", g.getStatus());
        m.put("ended", g.isEnded());
        return m;
    }
}
