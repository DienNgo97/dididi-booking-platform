package com.dididi.booking.bulk.web;

import com.dididi.booking.bulk.api.dto.BulkLineResult;
import com.dididi.booking.bulk.service.BulkBookingService;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.domain.entity.RoomType;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.hotel.repository.RoomTypeRepository;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.web.CurrentUser;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class BulkBookingWebController {

    private static final int ROWS = 6;

    private final BulkBookingService bulkBookingService;
    private final HotelRepository hotelRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final CurrentUser currentUser;

    public BulkBookingWebController(BulkBookingService bulkBookingService, HotelRepository hotelRepository,
                                    RoomTypeRepository roomTypeRepository, CurrentUser currentUser) {
        this.bulkBookingService = bulkBookingService;
        this.hotelRepository = hotelRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.currentUser = currentUser;
    }

    @GetMapping("/booking/bulk")
    public String form(@RequestParam Long hotelId, @RequestParam Long roomTypeId,
                       @RequestParam(required = false) String roomName,
                       Authentication auth, Model model) {
        Hotel h = hotelRepository.findById(hotelId).orElse(null);
        RoomType rt = roomTypeRepository.findById(roomTypeId).orElse(null);
        model.addAttribute("hotelId", hotelId);
        model.addAttribute("roomTypeId", roomTypeId);
        model.addAttribute("roomName", roomName);
        model.addAttribute("hotelName", h != null ? h.getName() : ("#" + hotelId));
        model.addAttribute("rows", ROWS);
        // Thông tin hạng phòng + giá để hiển thị trên form
        if (rt != null) {
            model.addAttribute("roomTypeName", rt.getName());
            model.addAttribute("roomCapacity", rt.getCapacity());
            model.addAttribute("roomPrice", rt.getBasePrice());
            model.addAttribute("roomCurrency", rt.getCurrency());
        }
        // Chỉ hiện tùy chọn "thanh toán bằng công ty" nếu user thuộc một công ty
        model.addAttribute("canPayByCompany", currentUser.require(auth).getCompanyId() != null);
        return "booking/bulk-form";
    }

    @PostMapping("/booking/bulk")
    public String submit(@RequestParam Long hotelId, @RequestParam Long roomTypeId,
                         @RequestParam(required = false) String roomName,
                         @RequestParam(required = false) String stay,
                         @RequestParam(required = false) List<String> guestName,
                         @RequestParam(required = false) List<String> checkIn,
                         @RequestParam(required = false) List<String> checkOut,
                         @RequestParam(required = false) List<String> dayDate,
                         @RequestParam(required = false) List<String> timeIn,
                         @RequestParam(required = false) List<String> timeOut,
                         @RequestParam(required = false) List<String> rooms,
                         @RequestParam(defaultValue = "false") boolean payByCompany,
                         Authentication auth, HttpSession session) {
        User u = currentUser.require(auth);
        // Chặn server-side: chỉ trả bằng công ty khi user thực sự thuộc công ty
        boolean pay = payByCompany && u.getCompanyId() != null;
        List<BulkLineResult> results = bulkBookingService.createBulk(
                u.getId(), hotelId, roomTypeId, roomName, stay,
                guestName, checkIn, checkOut, dayDate, timeIn, timeOut, rooms, pay);
        session.setAttribute("bulkResults", results);
        return "redirect:/booking/bulk/result";
    }

    @GetMapping("/booking/bulk/result")
    @SuppressWarnings("unchecked")
    public String result(HttpSession session, Model model) {
        Object data = session.getAttribute("bulkResults");
        if (data == null) {
            return "redirect:/account/bookings";
        }
        session.removeAttribute("bulkResults");
        model.addAttribute("results", (List<BulkLineResult>) data);
        return "booking/bulk-result";
    }
}
