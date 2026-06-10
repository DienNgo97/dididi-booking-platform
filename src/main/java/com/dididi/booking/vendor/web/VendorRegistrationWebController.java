package com.dididi.booking.vendor.web;

import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.domain.enums.HotelSource;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.Role;
import com.dididi.booking.identity.domain.enums.UserStatus;
import com.dididi.booking.identity.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Form web public cho vendor tu dang ky ban phong (song song voi REST POST /api/auth/vendor-register).
 * Tao tai khoan VENDOR (INACTIVE - cho duyet) + khach san DIRECT (an cho toi khi admin duyet).
 */
@Controller
public class VendorRegistrationWebController {

    private final UserRepository userRepository;
    private final HotelRepository hotelRepository;
    private final PasswordEncoder passwordEncoder;

    public VendorRegistrationWebController(UserRepository userRepository, HotelRepository hotelRepository,
                                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.hotelRepository = hotelRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/vendor-register")
    public String form() {
        return "vendor-register";
    }

    @PostMapping("/vendor-register")
    @Transactional
    public String register(@RequestParam String email,
                           @RequestParam String password,
                           @RequestParam(required = false) String fullName,
                           @RequestParam(required = false) String phone,
                           @RequestParam String hotelName,
                           @RequestParam(required = false) String city,
                           @RequestParam(required = false) String address,
                           @RequestParam(required = false) Integer starRating,
                           Model model, RedirectAttributes ra) {
        if (userRepository.existsByEmail(email)) {
            model.addAttribute("error", "Email đã được đăng ký. Vui lòng dùng email khác.");
            return "vendor-register";
        }

        User vendor = new User();
        vendor.setEmail(email);
        vendor.setPasswordHash(passwordEncoder.encode(password));
        vendor.setFullName(fullName);
        vendor.setPhone(phone);
        vendor.setRole(Role.VENDOR);
        vendor.setStatus(UserStatus.INACTIVE);   // chờ duyệt -> chưa đăng nhập được
        userRepository.save(vendor);

        Hotel hotel = new Hotel();
        hotel.setName(hotelName);
        hotel.setCity(city);
        hotel.setAddress(address);
        hotel.setStarRating(starRating);
        hotel.setActive(false);                   // ẩn cho tới khi duyệt
        hotel.setSource(HotelSource.DIRECT);
        hotel.setVendorId(vendor.getId());
        hotelRepository.save(hotel);

        ra.addFlashAttribute("message",
                "Đăng ký thành công! Tài khoản đang chờ admin duyệt — bạn sẽ nhận email khi được duyệt.");
        return "redirect:/vendor-register";
    }
}
