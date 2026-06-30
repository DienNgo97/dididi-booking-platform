package com.dididi.booking.group.web;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.common.QrCodeUtil;
import com.dididi.booking.group.domain.entity.GroupBooking;
import com.dididi.booking.group.service.GroupBookingService;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.invoice.service.InvoiceService;
import com.dididi.booking.payment.service.PaymentService;
import com.dididi.booking.payment.vnpay.VnPayService;
import com.dididi.booking.web.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Controller
public class GroupWebController {

    private final GroupBookingService groupService;
    private final HotelRepository hotelRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final PaymentService paymentService;
    private final VnPayService vnPayService;
    private final InvoiceService invoiceService;

    public GroupWebController(GroupBookingService groupService, HotelRepository hotelRepository,
                              UserRepository userRepository, CurrentUser currentUser,
                              PaymentService paymentService, VnPayService vnPayService,
                              InvoiceService invoiceService) {
        this.groupService = groupService;
        this.hotelRepository = hotelRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.paymentService = paymentService;
        this.vnPayService = vnPayService;
        this.invoiceService = invoiceService;
    }

    /** Form tao nhom (mo tu trang khach san). */
    @GetMapping("/groups/new")
    public String newGroup(@RequestParam Long hotelId, @RequestParam Long roomTypeId,
                           @RequestParam(required = false) String roomName,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
                           Model model) {
        Hotel h = hotelRepository.findById(hotelId).orElse(null);
        model.addAttribute("hotelId", hotelId);
        model.addAttribute("roomTypeId", roomTypeId);
        model.addAttribute("roomName", roomName);
        model.addAttribute("hotelName", h != null ? h.getName() : ("#" + hotelId));
        model.addAttribute("checkIn", checkIn != null ? checkIn.toString() : "");
        model.addAttribute("checkOut", checkOut != null ? checkOut.toString() : "");
        return "group/new";
    }

    @PostMapping("/groups/create")
    public String create(@RequestParam Long hotelId, @RequestParam Long roomTypeId,
                         @RequestParam(required = false) String roomName,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
                         @RequestParam(required = false) String title,
                         Authentication auth) {
        GroupBooking g = groupService.createGroup(currentUser.id(auth), hotelId, roomTypeId, roomName,
                checkIn, checkOut, title);
        return "redirect:/g/" + g.getToken();
    }

    /** Bang dieu khien nhom (xem duoc qua link). */
    @GetMapping("/g/{token}")
    public String dashboard(@PathVariable String token, Authentication auth, Model model) {
        GroupBooking g = groupService.getByToken(token);
        Hotel h = hotelRepository.findById(g.getHotelId()).orElse(null);

        List<MemberRow> rows = new ArrayList<>();
        BigDecimal totalAll = BigDecimal.ZERO, totalPaid = BigDecimal.ZERO;
        int paidCount = 0, unpaidCount = 0;
        for (Booking b : groupService.members(g.getId())) {
            BookingStatus s = b.getStatus();
            // Chi hien thi phong dang hoat dong: cho thanh toan + da xac nhan (an phong da xoa/het han).
            if (s != BookingStatus.PENDING_PAYMENT && s != BookingStatus.CONFIRMED) continue;
            String name = userRepository.findById(b.getUserId()).map(User::getFullName).orElse("Khách");
            boolean paid = s == BookingStatus.CONFIRMED;
            rows.add(new MemberRow(name, b.getQuantity(), b.getAmount(), s.name(), b.getPublicCode(), b.getUserId()));
            if (b.getAmount() != null) {
                totalAll = totalAll.add(b.getAmount());
                if (paid) totalPaid = totalPaid.add(b.getAmount());
            }
            if (paid) paidCount++; else unpaidCount++;
        }

        Long myId = currentUser.idOrNull(auth);
        model.addAttribute("group", g);
        model.addAttribute("hotelName", h != null ? h.getName() : ("#" + g.getHotelId()));
        model.addAttribute("hotelCity", h != null ? h.getCity() : "");
        model.addAttribute("members", rows);
        model.addAttribute("memberCount", rows.size());
        model.addAttribute("paidCount", paidCount);
        model.addAttribute("unpaidCount", unpaidCount);
        model.addAttribute("totalAll", totalAll);
        model.addAttribute("totalPaid", totalPaid);
        model.addAttribute("loggedIn", myId != null);
        model.addAttribute("myId", myId);
        model.addAttribute("myName", myId != null ? currentUser.require(auth).getFullName() : "");
        boolean isOrganizer = myId != null && myId.equals(g.getOrganizerUserId());
        model.addAttribute("isOrganizer", isOrganizer);
        // Hang phong cho ca form sua (chu nhom) lan form them phong (thanh vien).
        if (myId != null) {
            model.addAttribute("roomTypes", groupService.roomTypesFor(g.getHotelId()));
        }
        // Phase 2: trang thai dong/han chot/chia deu
        boolean closed = "CLOSED".equals(g.getStatus());
        boolean expired = g.getDeadline() != null && LocalDateTime.now().isAfter(g.getDeadline());
        boolean ended = g.isEnded();
        model.addAttribute("closed", closed);
        model.addAttribute("expired", expired);
        model.addAttribute("ended", ended);
        model.addAttribute("endedAt", g.getEndedAt());
        model.addAttribute("joinable", !closed && !expired && !ended);
        model.addAttribute("deadline", g.getDeadline());
        model.addAttribute("splitEven", g.isSplitEven());
        if (g.isSplitEven() && !rows.isEmpty()) {
            BigDecimal share = totalAll.divide(BigDecimal.valueOf(rows.size()), 0, java.math.RoundingMode.HALF_UP);
            model.addAttribute("evenShare", share);
        }
        return "group/dashboard";
    }

    /** Thanh vien them phong cua minh -> tao don -> sang trang thanh toan phan minh. */
    @PostMapping("/g/{token}/join")
    public String join(@PathVariable String token,
                       @RequestParam(required = false) String guestName,
                       @RequestParam(required = false) Long roomTypeId,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
                       @RequestParam(defaultValue = "1") int rooms,
                       @RequestParam(defaultValue = "false") boolean pay,
                       Authentication auth, RedirectAttributes ra) {
        GroupBooking g = groupService.getByToken(token);
        Long userId = currentUser.id(auth);
        String name = (guestName != null && !guestName.isBlank()) ? guestName.trim()
                : currentUser.require(auth).getFullName();
        LocalDate cin = checkIn != null ? checkIn : g.getCheckIn();
        LocalDate cout = checkOut != null ? checkOut : g.getCheckOut();
        try {
            Booking b = groupService.addMyRoom(g, userId, name, roomTypeId, cin, cout, rooms);
            if (pay) {
                return "redirect:/payment/" + b.getPublicCode();   // them & thanh toan ngay
            }
            ra.addFlashAttribute("message", "Đã thêm phòng vào nhóm. Bấm \"Thanh toán phần của tôi\" khi muốn trả.");
            return "redirect:/g/" + token;                         // chi them -> ve bang dieu khien
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/g/" + token;
        }
    }

    /** Xoa 1 phong khoi nhom (chu so huu phong hoac chu nhom, chi khi chua thanh toan). */
    @PostMapping("/g/{token}/room/{code}/delete")
    public String deleteRoom(@PathVariable String token, @PathVariable String code,
                             Authentication auth, RedirectAttributes ra) {
        try {
            groupService.removeRoom(token, code, currentUser.id(auth));
            ra.addFlashAttribute("message", "Đã xoá phòng khỏi nhóm");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/g/" + token;
    }

    /** Nguoi to chuc xoa han 1 thanh vien khoi nhom (huy moi phong chua thanh toan cua nguoi do). */
    @PostMapping("/g/{token}/member/{userId}/remove")
    public String removeMember(@PathVariable String token, @PathVariable Long userId,
                               Authentication auth, RedirectAttributes ra) {
        try {
            groupService.removeMember(token, userId, currentUser.id(auth));
            ra.addFlashAttribute("message", "Đã xoá thành viên khỏi nhóm");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/g/" + token;
    }

    /** Chu nhom thanh toan cho CA NHOM trong 1 giao dich VNPay (gop tong cac phong chua tra). */
    @GetMapping("/g/{token}/pay-group")
    public String payGroup(@PathVariable String token, Authentication auth, HttpServletRequest req, RedirectAttributes ra) {
        GroupBooking g = groupService.getByToken(token);
        Long myId = currentUser.idOrNull(auth);
        if (myId == null) return "redirect:/login";
        if (!myId.equals(g.getOrganizerUserId())) {
            ra.addFlashAttribute("error", "Chỉ người tạo nhóm mới thanh toán cho cả nhóm");
            return "redirect:/g/" + token;
        }
        // BP-GRP-01: chot dung tap phong PENDING tai thoi diem nay; total tinh tu chinh tap do.
        List<Booking> chosen = groupService.beginGroupPayment(g.getId());
        Booking lead = null;
        BigDecimal total = BigDecimal.ZERO;
        for (Booking b : chosen) {
            if (lead == null) lead = b;
            if (b.getAmount() != null) total = total.add(b.getAmount());
        }
        if (lead == null) {
            ra.addFlashAttribute("message", "Không có phòng nào cần thanh toán");
            return "redirect:/g/" + token;
        }
        String txnRef = "GRP" + g.getId() + "_" + System.currentTimeMillis();
        paymentService.initiateVnpayWithAmount(lead, total, txnRef);
        String url = vnPayService.createPaymentUrl(total, txnRef, "Thanh toan nhom #" + g.getId(), clientIp(req));
        return "redirect:" + url;
    }

    /** Dong nhom (chu nhom): khong cho them phong moi. */
    @PostMapping("/g/{token}/close")
    public String close(@PathVariable String token, Authentication auth, RedirectAttributes ra) {
        try {
            groupService.closeGroup(token, currentUser.id(auth));
            ra.addFlashAttribute("message", "Đã đóng nhóm");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/g/" + token;
    }

    /** Mo lai nhom (chu nhom). */
    @PostMapping("/g/{token}/reopen")
    public String reopen(@PathVariable String token, Authentication auth, RedirectAttributes ra) {
        try {
            groupService.reopenGroup(token, currentUser.id(auth));
            ra.addFlashAttribute("message", "Đã mở lại nhóm");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/g/" + token;
    }

    /** Chu nhom bam "Ket thuc chuyen di" -> mo khoa xuat hoa don chia tien. */
    @PostMapping("/g/{token}/end")
    public String endTrip(@PathVariable String token, Authentication auth, RedirectAttributes ra) {
        try {
            groupService.endTrip(token, currentUser.id(auth));
            ra.addFlashAttribute("message", "Đã kết thúc chuyến đi. Bạn có thể xuất hoá đơn chia tiền.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/g/" + token;
    }

    /** Mo lai chuyen di (huy ket thuc). */
    @PostMapping("/g/{token}/reopen-trip")
    public String reopenTrip(@PathVariable String token, Authentication auth, RedirectAttributes ra) {
        try {
            groupService.reopenTrip(token, currentUser.id(auth));
            ra.addFlashAttribute("message", "Đã mở lại chuyến đi");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/g/" + token;
    }

    /** Xuat PDF "Phieu chia tien nhom" (chu nhom, chi khi chuyen di da ket thuc). */
    @GetMapping("/g/{token}/invoice")
    @ResponseBody
    public ResponseEntity<?> invoice(@PathVariable String token, Authentication auth) {
        GroupBooking g = groupService.getByToken(token);
        Long myId = currentUser.idOrNull(auth);
        if (myId == null || !myId.equals(g.getOrganizerUserId())) {
            return ResponseEntity.status(403).body("Chỉ người tạo nhóm mới xuất được hoá đơn nhóm.");
        }
        if (!g.isEnded()) {
            return ResponseEntity.status(409).body("Hãy bấm \"Kết thúc chuyến đi\" trước khi xuất hoá đơn.");
        }
        List<Booking> paidRooms = new ArrayList<>();
        for (Booking b : groupService.members(g.getId())) {
            if (b.getStatus() == BookingStatus.CONFIRMED) paidRooms.add(b);
        }
        Hotel h = hotelRepository.findById(g.getHotelId()).orElse(null);
        User organizer = userRepository.findById(g.getOrganizerUserId()).orElse(null);
        byte[] pdf = invoiceService.generateGroupSettlement(g, paidRooms,
                h != null ? h.getName() : ("#" + g.getHotelId()),
                h != null ? h.getAddress() : null, organizer);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"ChiaTien-Nhom-" + token + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(new ByteArrayResource(pdf));
    }

    /** Ma QR tham gia nhom: encode link moi (cung URL trang /g/{token}). Anh PNG sinh tai server. */
    @GetMapping(value = "/g/{token}/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> qr(@PathVariable String token, HttpServletRequest req) {
        groupService.getByToken(token); // 404 neu khong ton tai
        String host = req.getHeader("Host");
        String base = req.getScheme() + "://"
                + (host != null && !host.isBlank() ? host : (req.getServerName() + ":" + req.getServerPort()));
        String inviteUrl = base + "/g/" + token;
        try {
            byte[] png = QrCodeUtil.png(inviteUrl, 240);
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    /** Nguoi to chuc sua nhom: doi ten va/hoac hang phong. */
    @PostMapping("/g/{token}/edit")
    public String edit(@PathVariable String token,
                       @RequestParam(required = false) String title,
                       @RequestParam(required = false) Long roomTypeId,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime deadline,
                       @RequestParam(defaultValue = "false") boolean splitEven,
                       Authentication auth, RedirectAttributes ra) {
        try {
            groupService.updateGroup(token, currentUser.id(auth), title, roomTypeId, deadline, splitEven);
            ra.addFlashAttribute("message", "Đã cập nhật nhóm");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/g/" + token;
    }

    /** Tab "Don nhom" trong Don cua toi: liet ke nhom user to chuc / da tham gia. */
    @GetMapping("/account/groups")
    public String myGroups(@RequestParam(required = false) String status, Authentication auth, Model model) {
        Long myId = currentUser.id(auth);
        List<GroupRow> planning = new ArrayList<>();
        List<GroupRow> ended = new ArrayList<>();
        for (GroupBooking g : groupService.myGroups(myId)) {
            Hotel h = hotelRepository.findById(g.getHotelId()).orElse(null);
            int active = 0, paid = 0;
            for (Booking b : groupService.members(g.getId())) {
                BookingStatus s = b.getStatus();
                if (s != BookingStatus.PENDING_PAYMENT && s != BookingStatus.CONFIRMED) continue;
                active++;
                if (s == BookingStatus.CONFIRMED) paid++;
            }
            boolean organizer = myId.equals(g.getOrganizerUserId());
            GroupRow row = new GroupRow(g.getToken(), g.getTitle(),
                    h != null ? h.getName() : ("#" + g.getHotelId()),
                    g.getCheckIn(), g.getCheckOut(), organizer, active, paid, g.isEnded());
            if (g.isEnded()) ended.add(row); else planning.add(row);
        }
        boolean endedTab = "ended".equals(status);
        model.addAttribute("activeTab", endedTab ? "ended" : "planning");
        model.addAttribute("groups", endedTab ? ended : planning);
        model.addAttribute("planningCount", planning.size());
        model.addAttribute("endedCount", ended.size());
        model.addAttribute("hasAny", !planning.isEmpty() || !ended.isEmpty());
        return "account/groups";
    }

    /** Dong hien thi 1 thanh vien tren bang dieu khien. */
    public record MemberRow(String name, int rooms, BigDecimal amount, String status, String code, Long userId) {}

    /** Dong hien thi 1 nhom trong tab "Don nhom". */
    public record GroupRow(String token, String title, String hotelName, LocalDate checkIn, LocalDate checkOut,
                           boolean organizer, int memberCount, int paidCount, boolean ended) {}
}
