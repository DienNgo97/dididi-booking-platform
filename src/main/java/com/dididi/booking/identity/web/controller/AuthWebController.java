package com.dididi.booking.identity.web.controller;

import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.identity.security.LoginAuditService;
import com.dididi.booking.identity.service.AccountService;
import com.dididi.booking.identity.service.OtpLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
public class AuthWebController {

    private final AccountService accountService;
    private final OtpLoginService otpLoginService;
    private final UserDetailsService userDetailsService;
    private final SessionRegistry sessionRegistry;
    private final LoginAuditService loginAuditService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthWebController(AccountService accountService, OtpLoginService otpLoginService,
                             UserDetailsService userDetailsService, SessionRegistry sessionRegistry,
                             LoginAuditService loginAuditService) {
        this.accountService = accountService;
        this.otpLoginService = otpLoginService;
        this.userDetailsService = userDetailsService;
        this.sessionRegistry = sessionRegistry;
        this.loginAuditService = loginAuditService;
    }

    /** Đã đăng nhập rồi thì không cho vào lại trang auth (login/register/forgot) -> về trang chủ. */
    private boolean loggedIn(Authentication auth) {
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String mode, Model model, Authentication auth) {
        if (loggedIn(auth)) return "redirect:/";
        model.addAttribute("mode", mode); // "password" -> hiện form mật khẩu; mặc định: bước nhập email (OTP)
        return "auth/login";
    }

    // ---------------- Đăng nhập bằng OTP qua email ----------------

    @PostMapping("/login/otp/request")
    public String requestOtp(@RequestParam String email, Model model) {
        OtpLoginService.RequestResult r = otpLoginService.request(email);
        if (r == OtpLoginService.RequestResult.INACTIVE) {
            return "redirect:/login?inactive";
        }
        // SENT hoặc NOT_FOUND: đều hiện bước nhập OTP (không tiết lộ email có tồn tại hay không)
        model.addAttribute("step", "otp");
        model.addAttribute("email", email);
        model.addAttribute("message",
                "Mã OTP đã được gửi tới email (nếu tồn tại trong hệ thống). Mã có hiệu lực 5 phút.");
        return "auth/login";
    }

    @PostMapping("/login/otp/verify")
    public String verifyOtp(@RequestParam String email, @RequestParam String otp,
                            HttpServletRequest request, HttpServletResponse response, Model model) {
        if (!otpLoginService.verify(email, otp)) {
            model.addAttribute("step", "otp");
            model.addAttribute("email", email);
            model.addAttribute("error", "Mã OTP không đúng hoặc đã hết hạn.");
            return "auth/login";
        }
        UserDetails ud = userDetailsService.loadUserByUsername(email);
        if (!ud.isEnabled()) {
            return "redirect:/login?inactive";
        }
        establishLogin(ud, request, response);
        loginAuditService.recordByEmail(email, "OTP");   // ghi nhat ky dang nhap (OTP qua email)
        return "redirect:/";
    }

    // ---------------- OTP qua AJAX (popup, tự đăng nhập khi nhập đúng) ----------------

    /** Gửi OTP (Ajax). Không tiết lộ email có tồn tại hay không; chỉ báo riêng trường hợp INACTIVE. */
    @PostMapping("/login/otp/request-ajax")
    @ResponseBody
    public Map<String, Object> requestOtpAjax(@RequestParam String email) {
        OtpLoginService.RequestResult r = otpLoginService.request(email);
        if (r == OtpLoginService.RequestResult.INACTIVE) {
            return Map.of("ok", false, "inactive", true,
                    "error", "Tài khoản chưa kích hoạt hoặc đã bị khoá.");
        }
        return Map.of("ok", true);
    }

    /** Xác thực OTP (Ajax). Đúng -> tự thiết lập phiên đăng nhập và trả về đường dẫn chuyển hướng. */
    @PostMapping("/login/otp/verify-ajax")
    @ResponseBody
    public Map<String, Object> verifyOtpAjax(@RequestParam String email, @RequestParam String otp,
                                             HttpServletRequest request, HttpServletResponse response) {
        if (!otpLoginService.verify(email, otp)) {
            return Map.of("ok", false, "error", "Mã OTP không đúng hoặc đã hết hạn.");
        }
        UserDetails ud = userDetailsService.loadUserByUsername(email);
        if (!ud.isEnabled()) {
            return Map.of("ok", false, "inactive", true,
                    "error", "Tài khoản chưa kích hoạt hoặc đã bị khoá.");
        }
        establishLogin(ud, request, response);
        loginAuditService.recordByEmail(email, "OTP");
        return Map.of("ok", true, "redirect", "/");
    }

    /** Thiết lập phiên đăng nhập thủ công (sau khi xác thực OTP). */
    private void establishLogin(UserDetails ud, HttpServletRequest request, HttpServletResponse response) {
        UsernamePasswordAuthenticationToken auth =
                UsernamePasswordAuthenticationToken.authenticated(ud, null, ud.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        request.getSession(true);
        request.changeSessionId(); // chống session fixation
        securityContextRepository.saveContext(context, request, response);
        // đăng ký phiên để concurrent-session + "đăng xuất mọi nơi" bao luôn phiên đăng nhập OTP
        sessionRegistry.registerNewSession(request.getSession().getId(), ud);
    }

    // ---------------- Đăng ký ----------------

    @GetMapping("/register")
    public String registerForm(Authentication auth) {
        if (loggedIn(auth)) return "redirect:/";
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String email,
                           @RequestParam String password,
                           @RequestParam(required = false) String confirmPassword,
                           @RequestParam(required = false) String fullName,
                           Model model, RedirectAttributes ra) {
        if (confirmPassword == null || !password.equals(confirmPassword)) {
            return registerError(model, email, fullName, "Mật khẩu nhập lại không khớp");
        }
        try {
            accountService.registerCustomer(email, password, fullName);
        } catch (BusinessException ex) {
            return registerError(model, email, fullName, ex.getMessage());
        }
        ra.addFlashAttribute("pendingActivation", email);
        return "redirect:/login";
    }

    // ---------------- Quên / đặt lại mật khẩu ----------------

    @GetMapping("/forgot-password")
    public String forgotForm(Authentication auth) {
        if (loggedIn(auth)) return "redirect:/";
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotSubmit(@RequestParam String email, RedirectAttributes ra) {
        accountService.requestPasswordReset(email);
        ra.addFlashAttribute("message",
                "Nếu email tồn tại trong hệ thống, chúng tôi đã gửi liên kết đặt lại mật khẩu. Vui lòng kiểm tra hộp thư.");
        return "redirect:/forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetForm(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("tokenValid", accountService.isResetTokenValid(token));
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String resetSubmit(@RequestParam String token,
                              @RequestParam String password,
                              @RequestParam(required = false) String confirmPassword,
                              Model model, RedirectAttributes ra) {
        model.addAttribute("token", token);
        model.addAttribute("tokenValid", true);
        if (confirmPassword == null || !password.equals(confirmPassword)) {
            model.addAttribute("error", "Mật khẩu nhập lại không khớp");
            return "auth/reset-password";
        }
        boolean ok;
        try {
            ok = accountService.resetPassword(token, password);
        } catch (BusinessException ex) {
            model.addAttribute("error", ex.getMessage());
            return "auth/reset-password";
        }
        if (!ok) {
            model.addAttribute("tokenValid", false);
            model.addAttribute("error", "Liên kết không hợp lệ hoặc đã hết hạn. Vui lòng yêu cầu lại.");
            return "auth/reset-password";
        }
        ra.addFlashAttribute("message", "Đặt lại mật khẩu thành công. Mời bạn đăng nhập.");
        return "redirect:/login?reset";
    }

    // ---------------- Kích hoạt tài khoản ----------------

    @GetMapping("/verify")
    public String verify(@RequestParam(required = false) String token) {
        boolean ok = accountService.verifyEmail(token);
        return ok ? "redirect:/login?verified" : "redirect:/login?verifyfail";
    }

    // ---------------- helpers ----------------

    private String registerError(Model model, String email, String fullName, String message) {
        model.addAttribute("error", message);
        model.addAttribute("email", email);
        model.addAttribute("fullName", fullName);
        return "auth/register";
    }
}
