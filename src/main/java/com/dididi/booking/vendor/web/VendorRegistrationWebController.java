package com.dididi.booking.vendor.web;

import com.dididi.booking.common.i18n.I18nSupport;

import com.dididi.booking.hotel.domain.CityGeo;
import com.dididi.booking.hotel.domain.HotelSupport;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.domain.enums.Amenity;
import com.dididi.booking.hotel.domain.enums.HotelSource;
import com.dididi.booking.hotel.domain.enums.PropertyType;
import com.dididi.booking.hotel.repository.HotelRepository;

import java.util.List;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.Role;
import com.dididi.booking.identity.domain.enums.UserStatus;
import com.dididi.booking.identity.repository.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
    private final com.dididi.booking.notification.EmailService emailService;

    public VendorRegistrationWebController(UserRepository userRepository, HotelRepository hotelRepository,
                                           PasswordEncoder passwordEncoder,
                                           com.dididi.booking.notification.EmailService emailService) {
        this.userRepository = userRepository;
        this.hotelRepository = hotelRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    /** Danh sách loại hình & tiện ích cho form (luôn có ở mọi handler của controller này). */
    @ModelAttribute("propertyTypes")
    public PropertyType[] propertyTypes() { return PropertyType.values(); }

    @ModelAttribute("amenityList")
    public Amenity[] amenityList() { return Amenity.values(); }

    /**
     * Bảng toạ độ trung tâm các TP (từ CityGeo — MỘT nguồn sự thật với fallback backend) bơm xuống JS:
     * gõ Tỉnh/Thành phố xong bản đồ tự bay tới đó, ghim tay chỉ còn là tinh chỉnh (QA TC-C-01).
     */
    @ModelAttribute("cityGeoJs")
    public java.util.Map<String, double[]> cityGeoJs() {
        java.util.Map<String, double[]> out = new java.util.LinkedHashMap<>();
        com.dididi.booking.hotel.domain.CityGeo.all()
                .forEach((name, g) -> out.put(name, new double[]{g.lat(), g.lng()}));
        return out;
    }

    @GetMapping("/vendor-register")
    public String form(Authentication auth) {
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return "redirect:/";
        }
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
                           // Nhom 1: dia chi tach + Nhom 2: toa do pin tren ban do
                           @RequestParam(required = false) String houseNumber,
                           @RequestParam(required = false) String street,
                           @RequestParam(required = false) String ward,
                           @RequestParam(required = false) String district,
                           @RequestParam(required = false) Double lat,
                           @RequestParam(required = false) Double lng,
                           @RequestParam(required = false) String propertyType,
                           @RequestParam(required = false) List<String> amenities,
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
        hotel.setHouseNumber(houseNumber);
        hotel.setStreet(street);
        hotel.setWard(ward);
        hotel.setDistrict(district);
        hotel.setProvince(city);
        String addr = (address != null && !address.isBlank())
                ? address
                : HotelSupport.composeAddress(houseNumber, street, ward, district, city, city);
        hotel.setAddress(addr);
        hotel.setLat(lat);
        hotel.setLng(lng);
        // suy region tu thanh pho; neu vendor chua pin thi dat tam toa do trung tam thanh pho
        CityGeo.Geo geo = CityGeo.lookup(city);
        if (geo != null) {
            hotel.setRegion(geo.region());
            if (hotel.getLat() == null || hotel.getLng() == null) {
                hotel.setLat(geo.lat());
                hotel.setLng(geo.lng());
            }
        }
        PropertyType pt = HotelSupport.parseEnum(PropertyType.class, propertyType);
        if (pt != null) hotel.setPropertyType(pt);
        if (amenities != null) hotel.setAmenities(HotelSupport.parseEnumSet(Amenity.class, amenities));
        hotel.setStarRating(starRating);
        hotel.setActive(false);                   // ẩn cho tới khi duyệt
        hotel.setSource(HotelSource.DIRECT);
        hotel.setVendorId(vendor.getId());
        hotelRepository.save(hotel);

        // QA TC-C-01: xác nhận ngay qua email là ĐÃ NHẬN hồ sơ (async — lỗi SMTP không chặn đăng ký).
        emailService.sendVendorRegistered(email, hotelName,
                org.springframework.context.i18n.LocaleContextHolder.getLocale());

        ra.addFlashAttribute("message", I18nSupport.msg("flash.f50",
                "Đăng ký thành công! Chúng tôi đã gửi email xác nhận — tài khoản đang chờ admin duyệt."));
        return "redirect:/vendor-register";
    }
}
