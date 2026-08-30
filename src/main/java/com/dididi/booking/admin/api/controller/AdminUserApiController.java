package com.dididi.booking.admin.api.controller;

import com.dididi.booking.admin.api.dto.AdminUserDto;
import com.dididi.booking.admin.api.dto.CreateAdminRequest;
import com.dididi.booking.audit.event.AuditEvent;
import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.dto.PagedResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.common.security.RoleUtils;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.Role;
import com.dididi.booking.identity.domain.enums.UserStatus;
import com.dididi.booking.identity.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Users", description = "Đổi role / tạo admin cần SUPER_ADMIN. Có ghi audit.")
@RestController
@RequestMapping("/api/admin/v1/users")
public class AdminUserApiController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final com.dididi.booking.identity.service.ProfileService profileService;

    public AdminUserApiController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                  ApplicationEventPublisher events,
                                  com.dididi.booking.identity.service.ProfileService profileService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.profileService = profileService;
    }

    @Operation(summary = "Danh sách user (phân trang, lọc theo role + status tuỳ chọn)")
    @GetMapping
    public ApiResponse<PagedResponse<AdminUserDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String q) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> result;
        if (q != null && !q.isBlank()) {
            // Thanh tìm kiếm tab Người dùng: email / họ tên / SĐT, vẫn tôn trọng lọc role/status.
            result = userRepository.adminSearch(q.trim(), role, status, pageable);
        } else if (role != null && status != null) {
            result = userRepository.findByRoleAndStatus(role, status, pageable);
        } else if (role != null) {
            result = userRepository.findByRole(role, pageable);
        } else if (status != null) {
            result = userRepository.findByStatus(status, pageable);
        } else {
            result = userRepository.findAll(pageable);
        }
        return ApiResponse.ok(PagedResponse.of(result.map(AdminUserDto::from)));
    }

    @Operation(summary = "Chi tiết user theo id")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminUserDto>> get(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(u -> ResponseEntity.ok(ApiResponse.ok(AdminUserDto.from(u))))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Tạo tài khoản quản trị (SUPER_ADMIN). role mặc định ADMIN.")
    @PostMapping
    public ApiResponse<AdminUserDto> create(@RequestBody CreateAdminRequest req, Authentication auth) {
        RoleUtils.requireSuperAdmin(auth);
        if (req.email() == null || req.email().isBlank()) {
            throw new BusinessException("INVALID", "Thiếu email", HttpStatus.BAD_REQUEST);
        }
        if (userRepository.existsByEmail(req.email())) {
            throw new BusinessException("EMAIL_EXISTS", "Email đã tồn tại", HttpStatus.CONFLICT);
        }
        if (req.password() == null || req.password().length() < 6) {
            throw new BusinessException("INVALID", "Mật khẩu tối thiểu 6 ký tự", HttpStatus.BAD_REQUEST);
        }
        User u = new User();
        u.setEmail(req.email());
        u.setFullName(req.fullName());
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setRole(req.role() == null ? Role.ADMIN : req.role());
        u.setStatus(UserStatus.ACTIVE);
        userRepository.save(u);
        events.publishEvent(new AuditEvent(Long.valueOf(auth.getName()), "CREATE_USER", "USER", u.getId(),
                "role=" + u.getRole() + ", email=" + u.getEmail()));
        return ApiResponse.ok(AdminUserDto.from(u), "Đã tạo tài khoản");
    }

    @Operation(summary = "Đổi trạng thái user (ACTIVE/INACTIVE/LOCKED) - có audit. "
            + "Đổi trạng thái ADMIN/SUPER_ADMIN/VENDOR cần SUPER_ADMIN; không thể tự nhắm mình.")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AdminUserDto>> changeStatus(@PathVariable Long id,
                                                                  @RequestParam UserStatus status,
                                                                  Authentication auth) {
        return userRepository.findById(id)
                .map(u -> {
                    // SEC-05: cam tu nham minh (tranh tu khoa/mo khoa quyen).
                    Long actorId = Long.valueOf(auth.getName());
                    if (actorId.equals(u.getId())) {
                        throw new BusinessException("SELF_TARGET",
                                "Không thể đổi trạng thái của chính mình", HttpStatus.BAD_REQUEST);
                    }
                    // SEC-05: doi trang thai user co quyen (ADMIN/SUPER_ADMIN/VENDOR) -> chi SUPER_ADMIN.
                    Role targetRole = u.getRole();
                    if (targetRole == Role.ADMIN || targetRole == Role.SUPER_ADMIN || targetRole == Role.VENDOR) {
                        RoleUtils.requireSuperAdmin(auth);
                    }
                    u.setStatus(status);
                    userRepository.save(u);
                    events.publishEvent(new AuditEvent(actorId,
                            "CHANGE_USER_STATUS", "USER", id, "status=" + status));
                    return ResponseEntity.ok(ApiResponse.ok(AdminUserDto.from(u), "Status updated"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Đổi vai trò user (SUPER_ADMIN) - có audit")
    @PatchMapping("/{id}/role")
    public ResponseEntity<ApiResponse<AdminUserDto>> changeRole(@PathVariable Long id,
                                                                @RequestParam Role role,
                                                                Authentication auth) {
        RoleUtils.requireSuperAdmin(auth);
        // SEC: khong cho tu ha quyen chinh minh -> tranh tu khoa toan bo quyen quan tri (lockout).
        Long actorId = Long.valueOf(auth.getName());
        if (actorId.equals(id) && role != Role.SUPER_ADMIN) {
            throw new BusinessException("SELF_DEMOTE", "Không thể tự hạ quyền của chính mình", HttpStatus.BAD_REQUEST);
        }
        return userRepository.findById(id)
                .map(u -> {
                    u.setRole(role);
                    userRepository.save(u);
                    events.publishEvent(new AuditEvent(actorId,
                            "CHANGE_USER_ROLE", "USER", id, "role=" + role));
                    return ResponseEntity.ok(ApiResponse.ok(AdminUserDto.from(u), "Role updated"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Ngày sinh của khách CHỈ NHẬP MỘT LẦN (chống đặt "sinh nhật giả" để lấy voucher), nên đây là
     * đường thoát duy nhất khi khách gõ nhầm. Thao tác này mở lại quyền nhận quà nên luôn ghi audit
     * kèm giá trị CŨ -> MỚI. Để trống ngày = xoá, khách được tự nhập lại một lần nữa.
     */
    @Operation(summary = "Sửa ngày sinh của khách (đường thoát khi nhập nhầm) — có audit")
    @PatchMapping("/{id}/birth-date")
    public ResponseEntity<ApiResponse<AdminUserDto>> changeBirthDate(@PathVariable Long id,
                                                                     @RequestParam(required = false) String birthDate,
                                                                     Authentication auth) {
        java.time.LocalDate moi;
        try {
            moi = (birthDate == null || birthDate.isBlank()) ? null : java.time.LocalDate.parse(birthDate.trim());
        } catch (Exception ex) {
            throw new BusinessException("INVALID_BIRTHDATE", "Ngày sinh không hợp lệ (yyyy-MM-dd)", HttpStatus.BAD_REQUEST);
        }
        return userRepository.findById(id)
                .map(u -> {
                    Long actorId = Long.valueOf(auth.getName());
                    java.time.LocalDate cu = profileService.adminSetBirthDate(id, moi);
                    events.publishEvent(new AuditEvent(actorId, "CHANGE_USER_BIRTHDATE", "USER", id,
                            "Sửa ngày sinh của " + u.getEmail() + ": " + cu + " -> " + moi
                                    + (moi == null ? " (xoá — khách được nhập lại)" : "")));
                    return ResponseEntity.ok(ApiResponse.ok(
                            AdminUserDto.from(userRepository.findById(id).orElse(u)), "Đã cập nhật ngày sinh"));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
