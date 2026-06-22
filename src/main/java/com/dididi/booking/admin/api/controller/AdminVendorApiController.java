package com.dididi.booking.admin.api.controller;

import com.dididi.booking.admin.api.dto.BanRequest;
import com.dididi.booking.admin.api.dto.CreateVendorRequest;
import com.dididi.booking.admin.api.dto.VendorAccountDto;
import com.dididi.booking.audit.event.AuditEvent;
import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.common.security.RoleUtils;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.domain.enums.HotelSource;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.Role;
import com.dididi.booking.identity.domain.enums.UserStatus;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.notification.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin - Vendors", description = "Tạo/duyệt vendor (khách sạn DIRECT). Cần JWT role ADMIN/SUPER_ADMIN")
@RestController
@RequestMapping("/api/admin/v1/vendors")
public class AdminVendorApiController {

    private final UserRepository userRepository;
    private final HotelRepository hotelRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ApplicationEventPublisher events;

    public AdminVendorApiController(UserRepository userRepository, HotelRepository hotelRepository,
                                    PasswordEncoder passwordEncoder, EmailService emailService,
                                    ApplicationEventPublisher events) {
        this.userRepository = userRepository;
        this.hotelRepository = hotelRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.events = events;
    }

    @Operation(summary = "Danh sách tất cả vendor + khách sạn + trạng thái")
    @GetMapping
    public ApiResponse<List<VendorAccountDto>> list() {
        return ApiResponse.ok(userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.VENDOR)
                .map(this::toDto).toList());
    }

    @Operation(summary = "Danh sách vendor đang chờ duyệt (INACTIVE)")
    @GetMapping("/pending")
    public ApiResponse<List<VendorAccountDto>> pending() {
        return ApiResponse.ok(userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.VENDOR && u.getStatus() == UserStatus.INACTIVE)
                .map(this::toDto).toList());
    }

    @Operation(summary = "Admin tạo tài khoản vendor + khách sạn DIRECT (kích hoạt ngay)")
    @PostMapping
    @Transactional
    public ApiResponse<VendorAccountDto> create(@Valid @RequestBody CreateVendorRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new BusinessException("EMAIL_EXISTS", "Email đã được đăng ký", HttpStatus.CONFLICT);
        }
        User vendor = new User();
        vendor.setEmail(req.email());
        vendor.setPasswordHash(passwordEncoder.encode(req.password()));
        vendor.setFullName(req.fullName());
        vendor.setPhone(req.phone());
        vendor.setRole(Role.VENDOR);
        vendor.setStatus(UserStatus.ACTIVE);
        userRepository.save(vendor);

        Hotel hotel = new Hotel();
        hotel.setName(req.hotelName());
        hotel.setCity(req.city());
        hotel.setAddress(req.address());
        hotel.setStarRating(req.starRating());
        hotel.setActive(true);
        hotel.setSource(HotelSource.DIRECT);
        hotel.setVendorId(vendor.getId());
        hotelRepository.save(hotel);

        return ApiResponse.ok(toDto(vendor), "Vendor created");
    }

    @Operation(summary = "Duyệt vendor: kích hoạt tài khoản + cho khách sạn hiển thị/đặt được")
    @PostMapping("/{userId}/approve")
    @Transactional
    public ApiResponse<VendorAccountDto> approve(@PathVariable Long userId, Authentication auth) {
        User u = vendorOrThrow(userId);
        u.setStatus(UserStatus.ACTIVE);
        userRepository.save(u);
        hotelRepository.findByVendorId(userId).ifPresent(h -> {
            h.setActive(true);
            hotelRepository.save(h);
        });
        emailService.sendVendorApproved(u.getId(),
                hotelRepository.findByVendorId(userId).map(Hotel::getName).orElse(null), LocaleContextHolder.getLocale());
        events.publishEvent(new AuditEvent(Long.valueOf(auth.getName()), "APPROVE_VENDOR", "USER", userId, "Duyệt vendor"));
        return ApiResponse.ok(toDto(u), "Đã duyệt vendor");
    }

    @Operation(summary = "Từ chối/khoá vendor: khoá tài khoản + ẩn khách sạn")
    @PostMapping("/{userId}/reject")
    @Transactional
    public ApiResponse<VendorAccountDto> reject(@PathVariable Long userId, Authentication auth) {
        User u = vendorOrThrow(userId);
        // Khoá 1 vendor ĐANG HOẠT ĐỘNG (ACTIVE) = ban -> chỉ SUPER_ADMIN.
        // Từ chối vendor đang chờ duyệt (INACTIVE) thì ADMIN thường vẫn làm được (đúng quy trình duyệt hồ sơ).
        if (u.getStatus() == UserStatus.ACTIVE) {
            RoleUtils.requireSuperAdmin(auth);
        }
        u.setStatus(UserStatus.LOCKED);
        userRepository.save(u);
        hotelRepository.findByVendorId(userId).ifPresent(h -> {
            h.setActive(false);
            hotelRepository.save(h);
        });
        emailService.sendVendorRejected(u.getId(), LocaleContextHolder.getLocale());
        events.publishEvent(new AuditEvent(Long.valueOf(auth.getName()), "REJECT_VENDOR", "USER", userId, "Từ chối vendor"));
        return ApiResponse.ok(toDto(u), "Đã từ chối vendor");
    }

    @Operation(summary = "Ban vendor (CHỈ Super Admin): khoá tài khoản + ẩn khách sạn + ghi audit")
    @PostMapping("/{userId}/ban")
    @Transactional
    public ApiResponse<VendorAccountDto> ban(@PathVariable Long userId,
                                             @RequestBody(required = false) BanRequest req,
                                             Authentication auth) {
        RoleUtils.requireSuperAdmin(auth);
        User u = vendorOrThrow(userId);
        u.setStatus(UserStatus.LOCKED);
        userRepository.save(u);
        hotelRepository.findByVendorId(userId).ifPresent(h -> {
            h.setActive(false);
            hotelRepository.save(h);
        });
        String reason = req != null ? req.reason() : null;
        events.publishEvent(new AuditEvent(Long.valueOf(auth.getName()), "BAN_VENDOR", "USER", userId,
                "Ban vendor " + u.getEmail() + (reason != null && !reason.isBlank() ? " — lý do: " + reason : "")));
        return ApiResponse.ok(toDto(u), "Đã ban vendor");
    }

    @Operation(summary = "Gỡ ban vendor (CHỈ Super Admin): mở khoá + hiện lại khách sạn + ghi audit")
    @PostMapping("/{userId}/unban")
    @Transactional
    public ApiResponse<VendorAccountDto> unban(@PathVariable Long userId, Authentication auth) {
        RoleUtils.requireSuperAdmin(auth);
        User u = vendorOrThrow(userId);
        u.setStatus(UserStatus.ACTIVE);
        userRepository.save(u);
        hotelRepository.findByVendorId(userId).ifPresent(h -> {
            h.setActive(true);
            hotelRepository.save(h);
        });
        events.publishEvent(new AuditEvent(Long.valueOf(auth.getName()), "UNBAN_VENDOR", "USER", userId,
                "Gỡ ban vendor " + u.getEmail()));
        return ApiResponse.ok(toDto(u), "Đã gỡ ban vendor");
    }

    private User vendorOrThrow(Long userId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy user", HttpStatus.NOT_FOUND));
        if (u.getRole() != Role.VENDOR) {
            throw new BusinessException("NOT_VENDOR", "User này không phải vendor", HttpStatus.BAD_REQUEST);
        }
        return u;
    }

    private VendorAccountDto toDto(User u) {
        Hotel h = hotelRepository.findByVendorId(u.getId()).orElse(null);
        return new VendorAccountDto(u.getId(), u.getEmail(), u.getFullName(), u.getStatus().name(),
                h == null ? null : h.getId(),
                h == null ? null : h.getName(),
                h != null && h.isActive());
    }
}
