package com.dididi.booking.admin.api.controller;

import com.dididi.booking.admin.api.dto.AdminUserDto;
import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.dto.PagedResponse;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.Role;
import com.dididi.booking.identity.domain.enums.UserStatus;
import com.dididi.booking.identity.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Users", description = "Cần JWT role ADMIN/SUPER_ADMIN/VENDOR")
@RestController
@RequestMapping("/api/admin/v1/users")
public class AdminUserApiController {

    private final UserRepository userRepository;

    public AdminUserApiController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Operation(summary = "Danh sách user (phân trang, lọc theo role tuỳ chọn)")
    @GetMapping
    public ApiResponse<PagedResponse<AdminUserDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Role role) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> result = (role == null)
                ? userRepository.findAll(pageable)
                : userRepository.findByRole(role, pageable);
        return ApiResponse.ok(PagedResponse.of(result.map(AdminUserDto::from)));
    }

    @Operation(summary = "Chi tiết user theo id")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminUserDto>> get(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(u -> ResponseEntity.ok(ApiResponse.ok(AdminUserDto.from(u))))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Đổi trạng thái user (ACTIVE/INACTIVE/LOCKED)")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AdminUserDto>> changeStatus(@PathVariable Long id,
                                                                  @RequestParam UserStatus status) {
        return userRepository.findById(id)
                .map(u -> {
                    u.setStatus(status);
                    userRepository.save(u);
                    return ResponseEntity.ok(ApiResponse.ok(AdminUserDto.from(u), "Status updated"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Đổi vai trò user (CUSTOMER/VENDOR/ADMIN/SUPER_ADMIN)")
    @PatchMapping("/{id}/role")
    public ResponseEntity<ApiResponse<AdminUserDto>> changeRole(@PathVariable Long id,
                                                                @RequestParam Role role) {
        return userRepository.findById(id)
                .map(u -> {
                    u.setRole(role);
                    userRepository.save(u);
                    return ResponseEntity.ok(ApiResponse.ok(AdminUserDto.from(u), "Role updated"));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
