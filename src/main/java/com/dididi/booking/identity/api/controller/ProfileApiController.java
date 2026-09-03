package com.dididi.booking.identity.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.UserStatus;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.identity.service.AccountService;
import com.dididi.booking.identity.service.ProfileService;
import com.dididi.booking.identity.service.RefreshTokenService;
import com.dididi.booking.social.service.SocialMediaService;
import com.dididi.booking.social.service.SocialProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hồ sơ của tôi cho khách (JWT: principal = userId).
 * Dùng lại ProfileService (tên/SĐT) + AccountService (đổi mật khẩu) như bản web /account/profile.
 */
@Tag(name = "Profile (khách)")
@RestController
@RequestMapping("/api/v1/profile")
public class ProfileApiController {

    private final UserRepository userRepository;
    private final ProfileService profileService;
    private final AccountService accountService;
    private final RefreshTokenService refreshTokenService;
    private final SocialProfileService socialProfileService;
    private final SocialMediaService socialMediaService;

    public ProfileApiController(UserRepository userRepository, ProfileService profileService,
                                AccountService accountService, RefreshTokenService refreshTokenService,
                                SocialProfileService socialProfileService, SocialMediaService socialMediaService) {
        this.userRepository = userRepository;
        this.profileService = profileService;
        this.accountService = accountService;
        this.refreshTokenService = refreshTokenService;
        this.socialProfileService = socialProfileService;
        this.socialMediaService = socialMediaService;
    }

    private Long uid(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        return Long.valueOf(auth.getName());
    }

    @Operation(summary = "Hồ sơ của tôi (email, tên, SĐT, trạng thái xác thực, vai trò)")
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(Authentication auth) {
        User u = userRepository.findById(uid(auth))
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", u.getId());
        out.put("email", u.getEmail());
        out.put("fullName", u.getFullName());
        out.put("phone", u.getPhone());
        out.put("birthDate", u.getBirthDate() == null ? null : u.getBirthDate().toString());
        out.put("phoneVerified", u.isPhoneVerified());
        out.put("role", u.getRole().name());
        out.put("emailVerified", u.getStatus() == UserStatus.ACTIVE);
        // Ảnh đại diện dùng chung với hồ sơ Cộng đồng; null = app vẽ chữ cái đầu.
        out.put("avatarUrl", socialProfileService.findByUserId(u.getId())
                .map(p -> SocialProfileService.avatarUrl(u.getId(), p.getAvatarKey())).orElse(null));
        return ApiResponse.ok(out);
    }

    @Operation(summary = "Đổi ảnh đại diện (dùng chung với hồ sơ Cộng đồng)")
    @PostMapping("/avatar")
    public ApiResponse<Map<String, Object>> updateAvatar(@RequestPart("image") MultipartFile image,
                                                         Authentication auth) {
        Long id = uid(auth);
        if (image == null || image.isEmpty()) {
            throw new BusinessException("NO_FILE", "Vui lòng chọn ảnh", HttpStatus.BAD_REQUEST);
        }
        String key = socialMediaService.uploadAvatar(image);
        socialProfileService.setAvatarKey(id, key);
        return ApiResponse.ok(Map.of("avatarUrl", SocialProfileService.avatarUrl(id, key)),
                "Đã cập nhật ảnh đại diện.");
    }

    @Operation(summary = "Gỡ ảnh đại diện")
    @DeleteMapping("/avatar")
    public ApiResponse<Void> removeAvatar(Authentication auth) {
        socialProfileService.setAvatarKey(uid(auth), null);
        return ApiResponse.ok(null, "Đã gỡ ảnh đại diện.");
    }

    @Operation(summary = "Cập nhật tên hiển thị")
    @PostMapping("/name")
    public ApiResponse<Void> updateName(@RequestBody Map<String, String> body, Authentication auth) {
        profileService.updateName(uid(auth), body.getOrDefault("fullName", ""));
        return ApiResponse.ok(null, "Đã cập nhật tên hiển thị.");
    }

    @Operation(summary = "Cập nhật ngày sinh (yyyy-MM-dd, để trống = xoá) — dùng cho quà sinh nhật")
    @PostMapping("/birthday")
    public ApiResponse<Void> updateBirthday(@RequestBody Map<String, String> body, Authentication auth) {
        String raw = body.get("birthDate");
        java.time.LocalDate d;
        try {
            d = (raw == null || raw.isBlank()) ? null : java.time.LocalDate.parse(raw.trim());
        } catch (Exception ex) {
            throw new BusinessException("INVALID_BIRTHDATE", "Ngày sinh không hợp lệ (yyyy-MM-dd)", HttpStatus.BAD_REQUEST);
        }
        profileService.updateBirthDate(uid(auth), d);
        return ApiResponse.ok(null, d == null ? "Đã xoá ngày sinh." : "Đã cập nhật ngày sinh.");
    }

    @Operation(summary = "Đổi mật khẩu (đăng xuất các phiên khác)")
    @PostMapping("/password")
    public ApiResponse<Void> changePassword(@RequestBody Map<String, String> body, Authentication auth) {
        String current = body.get("currentPassword");
        String next = body.getOrDefault("newPassword", "");
        String confirm = body.getOrDefault("confirmPassword", next);
        if (!next.equals(confirm)) {
            throw new BusinessException("PASSWORD_MISMATCH", "Mật khẩu xác nhận không khớp");
        }
        accountService.changePassword(uid(auth), current, next, null);
        return ApiResponse.ok(null, "Đã đổi mật khẩu.");
    }

    @Operation(summary = "Gửi OTP xác thực số điện thoại")
    @PostMapping("/phone/send")
    public ApiResponse<Void> sendPhoneOtp(@RequestBody Map<String, String> body, Authentication auth) {
        profileService.startPhoneVerification(uid(auth), body.getOrDefault("phone", ""));
        return ApiResponse.ok(null, "Đã gửi mã OTP tới số điện thoại.");
    }

    @Operation(summary = "Xác nhận OTP số điện thoại")
    @PostMapping("/phone/confirm")
    public ApiResponse<Void> confirmPhone(@RequestBody Map<String, String> body, Authentication auth) {
        profileService.confirmPhone(uid(auth), body.getOrDefault("code", ""));
        return ApiResponse.ok(null, "Đã xác thực số điện thoại.");
    }

    @Operation(summary = "Huỷ liên kết tài khoản Google")
    @PostMapping("/google/unlink")
    public ApiResponse<Void> unlinkGoogle(Authentication auth) {
        profileService.unlinkGoogle(uid(auth));
        return ApiResponse.ok(null, "Đã huỷ liên kết Google.");
    }

    @Operation(summary = "Gửi lại email kích hoạt (chỉ khi tài khoản chưa kích hoạt)")
    @PostMapping("/email/resend")
    public ApiResponse<Void> resendActivation(Authentication auth) {
        accountService.resendVerification(uid(auth));
        return ApiResponse.ok(null, "Đã gửi lại email kích hoạt.");
    }

    @Operation(summary = "Đóng (khoá) tài khoản — cần mật khẩu nếu tài khoản có đặt mật khẩu")
    @PostMapping("/close")
    public ApiResponse<Void> closeAccount(@RequestBody Map<String, String> body, Authentication auth) {
        accountService.closeAccount(uid(auth), body.getOrDefault("password", ""));
        return ApiResponse.ok(null, "Đã đóng tài khoản.");
    }

    @Operation(summary = "Danh sách thiết bị/phiên đăng nhập. Gửi header X-Refresh-Token để đánh dấu phiên hiện tại.")
    @GetMapping("/sessions")
    public ApiResponse<List<Map<String, Object>>> sessions(
            @RequestHeader(value = "X-Refresh-Token", required = false) String currentRefreshToken,
            Authentication auth) {
        Long userId = uid(auth);
        String currentSid = refreshTokenService.sessionIdOf(currentRefreshToken);
        List<Map<String, Object>> out = new ArrayList<>();
        for (RefreshTokenService.SessionInfo s : refreshTokenService.listSessions(userId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.id());
            m.put("device", s.device());
            m.put("createdAt", s.createdAtEpoch() * 1000); // ms cho client
            m.put("current", currentSid != null && currentSid.equals(s.id()));
            out.add(m);
        }
        return ApiResponse.ok(out);
    }

    @Operation(summary = "Thu hồi (đăng xuất) một thiết bị/phiên theo id")
    @PostMapping("/sessions/{sessionId}/revoke")
    public ApiResponse<Void> revokeSession(@PathVariable String sessionId, Authentication auth) {
        boolean ok = refreshTokenService.revokeBySessionId(uid(auth), sessionId);
        if (!ok) {
            throw new BusinessException("SESSION_NOT_FOUND", "Không tìm thấy phiên này", HttpStatus.NOT_FOUND);
        }
        return ApiResponse.ok(null, "Đã đăng xuất thiết bị.");
    }
}
