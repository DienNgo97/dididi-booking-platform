package com.dididi.booking.vendor.api.controller;

import com.dididi.booking.admin.api.dto.VendorAccountDto;
import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.domain.enums.HotelSource;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.Role;
import com.dididi.booking.identity.domain.enums.UserStatus;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.vendor.api.dto.VendorRegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "Vendor tự đăng ký (public) — tài khoản tạo ra ở trạng thái CHỜ DUYỆT")
@RestController
@RequestMapping("/api/auth")
public class VendorRegistrationApiController {

    private final UserRepository userRepository;
    private final HotelRepository hotelRepository;
    private final PasswordEncoder passwordEncoder;

    public VendorRegistrationApiController(UserRepository userRepository, HotelRepository hotelRepository,
                                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.hotelRepository = hotelRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Operation(summary = "Vendor đăng ký bán phòng — tạo tài khoản VENDOR + khách sạn DIRECT (chờ admin duyệt)")
    @PostMapping("/vendor-register")
    @Transactional
    public ApiResponse<VendorAccountDto> register(@Valid @RequestBody VendorRegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new BusinessException("EMAIL_EXISTS", "Email đã được đăng ký", HttpStatus.CONFLICT);
        }
        User vendor = new User();
        vendor.setEmail(req.email());
        vendor.setPasswordHash(passwordEncoder.encode(req.password()));
        vendor.setFullName(req.fullName());
        vendor.setPhone(req.phone());
        vendor.setRole(Role.VENDOR);
        vendor.setStatus(UserStatus.INACTIVE); // cho duyet -> chua dang nhap duoc
        userRepository.save(vendor);

        Hotel hotel = new Hotel();
        hotel.setName(req.hotelName());
        hotel.setCity(req.city());
        hotel.setAddress(req.address());
        hotel.setStarRating(req.starRating());
        hotel.setActive(false); // chua hien thi toi khach toi khi duyet
        hotel.setSource(HotelSource.DIRECT);
        hotel.setVendorId(vendor.getId());
        hotelRepository.save(hotel);

        return ApiResponse.ok(
                new VendorAccountDto(vendor.getId(), vendor.getEmail(), vendor.getFullName(),
                        vendor.getStatus().name(), hotel.getId(), hotel.getName(), hotel.isActive()),
                "Đăng ký thành công. Tài khoản đang chờ admin duyệt.");
    }
}
