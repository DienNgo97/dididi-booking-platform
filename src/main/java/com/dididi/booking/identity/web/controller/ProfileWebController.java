package com.dididi.booking.identity.web.controller;

import com.dididi.booking.common.i18n.I18nSupport;

import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.UserStatus;
import com.dididi.booking.identity.service.AccountService;
import com.dididi.booking.identity.service.ProfileService;
import com.dididi.booking.social.service.SocialMediaService;
import com.dididi.booking.social.service.SocialProfileService;
import com.dididi.booking.web.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Trang "Hồ sơ của tôi" (web khách): bảo mật tài khoản + thông tin cá nhân.
 * Tất cả route nằm dưới /account/** nên đã yêu cầu đăng nhập (SecurityWebConfig).
 */
@Controller
public class ProfileWebController {

    private final CurrentUser currentUser;
    private final ProfileService profileService;
    private final AccountService accountService;
    private final SocialProfileService socialProfileService;
    private final SocialMediaService socialMediaService;

    public ProfileWebController(CurrentUser currentUser, ProfileService profileService,
                                AccountService accountService, SocialProfileService socialProfileService,
                                SocialMediaService socialMediaService) {
        this.currentUser = currentUser;
        this.profileService = profileService;
        this.accountService = accountService;
        this.socialProfileService = socialProfileService;
        this.socialMediaService = socialMediaService;
    }

    private static String sessionId(HttpServletRequest req) {
        var s = req.getSession(false);
        return s != null ? s.getId() : null;
    }

    // ---------------- Trang hồ sơ ----------------

    @GetMapping("/account/profile")
    public String profile(Authentication auth, Model model) {
        User u = currentUser.require(auth);
        model.addAttribute("user", u);
        model.addAttribute("emailVerified", u.getStatus() == UserStatus.ACTIVE);
        model.addAttribute("avatarUrl", avatarUrl(u.getId()));
        return "account/profile";
    }

    /**
     * ẢNH ĐẠI DIỆN — ghi vào ĐÚNG chỗ mà tab Cộng đồng đang dùng (social_profiles.avatar_key).
     * Cố tình KHÔNG tạo cột ảnh riêng cho tài khoản: hai chỗ lưu thì kiểu gì cũng có ngày lệch
     * nhau, còn một chỗ thì không có đường nào lệch được.
     */
    @PostMapping("/account/profile/avatar")
    public String updateAvatar(@RequestParam("file") MultipartFile file,
                               Authentication auth, RedirectAttributes ra) {
        Long uid = currentUser.id(auth);
        try {
            if (file == null || file.isEmpty()) {
                throw new BusinessException("NO_FILE", "Vui lòng chọn ảnh", HttpStatus.BAD_REQUEST);
            }
            socialProfileService.setAvatarKey(uid, socialMediaService.uploadAvatar(file));
            ra.addFlashAttribute("message", I18nSupport.msg("flash.f59", "Đã cập nhật ảnh đại diện."));
        } catch (BusinessException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/account/profile";
    }

    /** Gỡ ảnh, quay về hiển thị chữ cái đầu. */
    @PostMapping("/account/profile/avatar/remove")
    public String removeAvatar(Authentication auth, RedirectAttributes ra) {
        socialProfileService.setAvatarKey(currentUser.id(auth), null);
        ra.addFlashAttribute("message", I18nSupport.msg("flash.f60", "Đã gỡ ảnh đại diện."));
        return "redirect:/account/profile";
    }

    private String avatarUrl(Long userId) {
        return socialProfileService.findByUserId(userId)
                .map(p -> SocialProfileService.avatarUrl(userId, p.getAvatarKey()))
                .orElse(null);
    }

    // ---------------- Thông tin cá nhân ----------------

    @PostMapping("/account/profile/name")
    public String updateName(@RequestParam String fullName, Authentication auth, RedirectAttributes ra) {
        try {
            profileService.updateName(currentUser.id(auth), fullName);
            ra.addFlashAttribute("message", I18nSupport.msg("flash.f15", "Đã cập nhật tên hiển thị."));
        } catch (BusinessException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/account/profile";
    }

    /**
     * Ngày sinh (cho chương trình quà sinh nhật) — CHỈ NHẬP MỘT LẦN, không tự xoá được.
     * Nhập nhầm thì liên hệ CSKH để admin sửa (có ghi audit).
     */
    @PostMapping("/account/profile/birthday")
    public String updateBirthday(@RequestParam(required = false) String birthDate,
                                 Authentication auth, RedirectAttributes ra) {
        try {
            java.time.LocalDate d = (birthDate == null || birthDate.isBlank())
                    ? null : java.time.LocalDate.parse(birthDate.trim());
            profileService.updateBirthDate(currentUser.id(auth), d);
            ra.addFlashAttribute("message", "Đã lưu ngày sinh. Thông tin này chỉ nhập một lần.");
        } catch (BusinessException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            ra.addFlashAttribute("error", I18nSupport.msg("flash.f04", "Ngày sinh không hợp lệ."));
        }
        return "redirect:/account/profile";
    }

    // ---------------- Số điện thoại (OTP) ----------------

    @PostMapping("/account/profile/phone/send")
    public String sendPhoneOtp(@RequestParam String phone, Authentication auth, RedirectAttributes ra) {
        try {
            profileService.startPhoneVerification(currentUser.id(auth), phone);
            ra.addFlashAttribute("message", I18nSupport.msg("flash.f24", "Đã gửi mã OTP. Vui lòng nhập mã để xác thực số điện thoại."));
        } catch (BusinessException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/account/profile";
    }

    @PostMapping("/account/profile/phone/confirm")
    public String confirmPhone(@RequestParam String code, Authentication auth, RedirectAttributes ra) {
        try {
            profileService.confirmPhone(currentUser.id(auth), code);
            ra.addFlashAttribute("message", I18nSupport.msg("flash.f36", "Đã xác thực số điện thoại."));
        } catch (BusinessException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/account/profile";
    }

    // ---------------- Mật khẩu ----------------

    @PostMapping("/account/profile/password")
    public String changePassword(@RequestParam(required = false) String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 Authentication auth, HttpServletRequest req, RedirectAttributes ra) {
        try {
            if (!newPassword.equals(confirmPassword)) {
                throw new BusinessException("PASSWORD_MISMATCH", "Mật khẩu xác nhận không khớp");
            }
            accountService.changePassword(currentUser.id(auth), currentPassword, newPassword, sessionId(req));
            ra.addFlashAttribute("message", I18nSupport.msg("flash.f42", "Đã đổi mật khẩu. Các thiết bị khác đã được đăng xuất."));
        } catch (BusinessException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/account/profile";
    }

    // ---------------- Liên kết Google ----------------

    @PostMapping("/account/profile/google/unlink")
    public String unlinkGoogle(Authentication auth, RedirectAttributes ra) {
        try {
            profileService.unlinkGoogle(currentUser.id(auth));
            ra.addFlashAttribute("message", I18nSupport.msg("flash.f19", "Đã gỡ liên kết Google."));
        } catch (BusinessException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/account/profile";
    }

    // ---------------- Email: gửi lại kích hoạt ----------------

    @PostMapping("/account/profile/email/resend")
    public String resendVerification(Authentication auth, RedirectAttributes ra) {
        accountService.resendVerification(currentUser.id(auth));
        ra.addFlashAttribute("message", I18nSupport.msg("flash.f23", "Đã gửi lại email kích hoạt (nếu tài khoản chưa xác thực)."));
        return "redirect:/account/profile";
    }

    // ---------------- Khóa đăng nhập / thiết bị ----------------

    @GetMapping("/account/profile/devices")
    public String devices(Authentication auth, HttpServletRequest req, Model model) {
        User u = currentUser.require(auth);
        model.addAttribute("sessions", accountService.listWebSessions(u.getEmail(), sessionId(req)));
        return "account/login-devices";
    }

    @PostMapping("/account/profile/devices/logout-others")
    public String logoutOthers(Authentication auth, HttpServletRequest req, RedirectAttributes ra) {
        User u = currentUser.require(auth);
        int n = accountService.logoutOtherDevices(u.getId(), u.getEmail(), sessionId(req));
        ra.addFlashAttribute("message", I18nSupport.msg("flash.f45", "Đã đăng xuất khỏi " + n + " thiết bị khác (và các phiên ứng dụng).", n));
        return "redirect:/account/profile/devices";
    }

    // ---------------- Xoá tài khoản ----------------

    @PostMapping("/account/profile/close")
    public String closeAccount(@RequestParam(required = false) String password,
                               Authentication auth, HttpServletRequest req, HttpServletResponse resp,
                               RedirectAttributes ra) {
        try {
            accountService.closeAccount(currentUser.id(auth), password);
        } catch (BusinessException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/account/profile";
        }
        // Đăng xuất NGAY phiên hiện tại (cả form-login lẫn Google) rồi về trang chủ ẩn danh.
        new SecurityContextLogoutHandler().logout(req, resp, auth);
        return "redirect:/?account-closed";
    }
}
