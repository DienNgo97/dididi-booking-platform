package com.dididi.booking.notification;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.internet.MimeMessage;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Gui email thong bao (Phase Dot1 #2) qua SMTP (Gmail).
 * NGUYEN TAC: gui PHONG THU - moi loi gui mail chi log canh bao, KHONG nem ra
 * lam hong luong dat/thanh toan/duyet. Co the tat bang app.mail.enabled=false.
 *
 * i18n: moi method nhan mot {@link Locale} (lay tu LocaleContextHolder o tang request,
 * truyen vao truoc khi qua bien gioi @Async). Noi dung lay tu MessageSource (email.*).
 * Locale null -> mac dinh tieng Viet.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final Locale DEFAULT_LOCALE = new Locale("vi");

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;
    private final MessageSource messages;

    @Value("${app.mail.enabled:true}")
    private boolean enabled;

    @Value("${app.mail.from:Dididi <no-reply@dididi.local>}")
    private String from;

    public EmailService(JavaMailSender mailSender, UserRepository userRepository, MessageSource messages) {
        this.mailSender = mailSender;
        this.userRepository = userRepository;
        this.messages = messages;
    }

    // ---------------- public API ----------------

    @Async
    public void sendBookingConfirmed(Booking b, Locale locale) {
        if (b == null) return;
        Locale loc = loc(locale);
        String to = emailOf(b.getUserId());
        String inner = p(m(loc, "email.confirmed.body"))
                + infoBox("<b>" + m(loc, "email.label.code") + ":</b> " + b.getPublicCode() + "<br/>"
                        + "<b>" + m(loc, "email.label.content") + ":</b> " + safe(b.getTitle()) + "<br/>"
                        + "<b>" + m(loc, "email.label.amount") + ":</b> " + money(b.getAmount()) + " " + b.getCurrency())
                + note(m(loc, "email.confirmed.note"));
        sendHtml(to, m(loc, "email.confirmed.subject", b.getPublicCode()),
                htmlShell(m(loc, "email.confirmed.heading"), inner, loc),
                m(loc, "email.confirmed.plain", b.getPublicCode(), money(b.getAmount()) + " " + b.getCurrency()));
    }

    @Async
    public void sendRefunded(Booking b, BigDecimal amount, Locale locale) {
        if (b == null) return;
        Locale loc = loc(locale);
        String to = emailOf(b.getUserId());
        String inner = p(m(loc, "email.refunded.body"))
                + infoBox("<b>" + m(loc, "email.label.code") + ":</b> " + b.getPublicCode() + "<br/>"
                        + "<b>" + m(loc, "email.label.refundAmount") + ":</b> " + money(amount) + " " + b.getCurrency())
                + note(m(loc, "email.refunded.note"));
        sendHtml(to, m(loc, "email.refunded.subject", b.getPublicCode()),
                htmlShell(m(loc, "email.refunded.heading"), inner, loc),
                m(loc, "email.refunded.plain", b.getPublicCode(), money(amount) + " " + b.getCurrency()));
    }

    @Async
    public void sendVendorApproved(Long userId, String hotelName, Locale locale) {
        Locale loc = loc(locale);
        String to = emailOf(userId);
        String inner = p(m(loc, "email.vendorApproved.body"))
                + (hotelName != null ? infoBox("<b>" + m(loc, "email.label.hotel") + ":</b> " + hotelName) : "");
        sendHtml(to, m(loc, "email.vendorApproved.subject"),
                htmlShell(m(loc, "email.vendorApproved.heading"), inner, loc),
                m(loc, "email.vendorApproved.plain") + (hotelName != null ? " " + m(loc, "email.label.hotel") + ": " + hotelName : ""));
    }

    /** Email xác nhận ĐÃ NHẬN hồ sơ đăng ký bán phòng — chờ admin duyệt (QA TC-C-01).
     *  Gửi ngay lúc nộp form; khi admin quyết sẽ có tiếp sendVendorApproved/Rejected. */
    @Async
    public void sendVendorRegistered(String to, String hotelName, Locale locale) {
        Locale loc = loc(locale);
        String inner = p(m(loc, "email.vendorRegistered.body1"))
                + (hotelName != null ? infoBox("<b>" + m(loc, "email.label.hotel") + ":</b> " + hotelName) : "")
                + p(m(loc, "email.vendorRegistered.body2"));
        sendHtml(to, m(loc, "email.vendorRegistered.subject"),
                htmlShell(m(loc, "email.vendorRegistered.heading"), inner, loc),
                m(loc, "email.vendorRegistered.plain"));
    }

    @Async
    public void sendVendorRejected(Long userId, Locale locale) {
        Locale loc = loc(locale);
        String to = emailOf(userId);
        String inner = p(m(loc, "email.vendorRejected.body1")) + p(m(loc, "email.vendorRejected.body2"));
        sendHtml(to, m(loc, "email.vendorRejected.subject"),
                htmlShell(m(loc, "email.vendorRejected.heading"), inner, loc),
                m(loc, "email.vendorRejected.plain"));
    }

    /** Email kích hoạt tài khoản sau khi đăng ký (HTML có thương hiệu). */
    @Async
    public void sendVerification(String to, String activationUrl, Locale locale) {
        Locale loc = loc(locale);
        String inner =
            "<p style=\"margin:0 0 16px;color:#3b4253;font-size:14px;line-height:1.6\">" + m(loc, "email.verify.body") + "</p>"
          + "<div style=\"text-align:center;margin:24px 0\">"
          + "<a href=\"" + activationUrl + "\" style=\"display:inline-block;background:#3dac78;color:#ffffff;"
          + "text-decoration:none;font-weight:700;font-size:15px;padding:13px 36px;border-radius:10px\">"
          + m(loc, "email.verify.button") + "</a></div>"
          + "<p style=\"margin:14px 0 0;color:#66707f;font-size:13px;line-height:1.6\">" + m(loc, "email.verify.fallback") + "<br/>"
          + "<a href=\"" + activationUrl + "\" style=\"color:#3dac78;word-break:break-all\">" + activationUrl + "</a></p>"
          + "<p style=\"margin:12px 0 0;color:#9aa3b2;font-size:12.5px\">" + m(loc, "email.verify.expiry") + "</p>";
        String html = htmlShell(m(loc, "email.verify.heading"), inner, loc);
        String plain = m(loc, "email.verify.plain", activationUrl);
        sendHtml(to, m(loc, "email.verify.subject"), html, plain);
    }

    /** Email đặt lại mật khẩu. */
    @Async
    public void sendPasswordReset(String to, String resetUrl, Locale locale) {
        Locale loc = loc(locale);
        String inner = p(m(loc, "email.reset.body"))
                + btn(resetUrl, m(loc, "email.reset.button"))
                + note(m(loc, "email.reset.fallback") + "<br/>"
                        + "<a href=\"" + resetUrl + "\" style=\"color:#3dac78;word-break:break-all\">" + resetUrl + "</a>")
                + note(m(loc, "email.reset.expiry"));
        sendHtml(to, m(loc, "email.reset.subject"), htmlShell(m(loc, "email.reset.heading"), inner, loc),
                m(loc, "email.reset.plain", resetUrl));
    }

    /** Mã OTP đăng nhập (HTML có thương hiệu), hiệu lực 5 phút. */
    @Async
    public void sendLoginOtp(String to, String code, Locale locale) {
        Locale loc = loc(locale);
        String inner =
            "<p style=\"margin:0 0 10px;color:#3b4253;font-size:14px;line-height:1.6\">" + m(loc, "email.otp.body") + "</p>"
          + "<div style=\"text-align:center;margin:20px 0\">"
          + "<span style=\"display:inline-block;background:#e7f5ee;border:1px solid #cce8da;border-radius:12px;"
          + "padding:16px 28px;font-size:32px;font-weight:800;letter-spacing:12px;color:#2f8b60\">" + code + "</span>"
          + "</div>"
          + "<p style=\"margin:14px 0 0;color:#66707f;font-size:13px;line-height:1.6\">" + m(loc, "email.otp.expiry") + "</p>"
          + "<p style=\"margin:8px 0 0;color:#9aa3b2;font-size:12.5px\">" + m(loc, "email.otp.ignore") + "</p>";
        String html = htmlShell(m(loc, "email.otp.heading"), inner, loc);
        String plain = m(loc, "email.otp.plain", code);
        sendHtml(to, m(loc, "email.otp.subject", code), html, plain);
    }

    @Async
    public void sendCompanyInvite(String toEmail, String companyName, String acceptUrl, Locale locale) {
        Locale loc = loc(locale);
        String inner = p(m(loc, "email.invite.body", companyName))
                + btn(acceptUrl, m(loc, "email.invite.button"))
                + note(m(loc, "email.invite.note"));
        sendHtml(toEmail, m(loc, "email.invite.subject", companyName),
                htmlShell(m(loc, "email.invite.heading"), inner, loc),
                m(loc, "email.invite.plain", companyName, acceptUrl));
    }

    // ---------------- helpers ----------------

    /** Lay message theo locale; key thieu -> tra ve chinh key (khong lam vo email). */
    private String m(Locale loc, String code, Object... args) {
        Object[] a = (args == null || args.length == 0) ? null : args;
        return messages.getMessage(code, a, code, loc);
    }

    private static Locale loc(Locale l) {
        return l != null ? l : DEFAULT_LOCALE;
    }

    private void send(String to, String subject, String body) {
        if (!enabled) {
            log.debug("Mail tat (app.mail.enabled=false), bo qua: {}", subject);
            return;
        }
        if (to == null || to.isBlank()) {
            log.warn("Khong co email nguoi nhan, bo qua: {}", subject);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("Da gui email -> {} : {}", to, subject);
        } catch (Exception e) {
            log.warn("Gui email that bai (to={}, subject={}): {}", to, subject, e.getMessage());
        }
    }

    // ---- helper dựng nội dung HTML cho thân email ----
    private static String p(String html) {
        return "<p style=\"margin:0 0 14px;color:#3b4253;font-size:14px;line-height:1.6\">" + html + "</p>";
    }
    private static String note(String html) {
        return "<p style=\"margin:12px 0 0;color:#9aa3b2;font-size:12.5px;line-height:1.6\">" + html + "</p>";
    }
    private static String btn(String url, String label) {
        return "<div style=\"text-align:center;margin:22px 0\"><a href=\"" + url + "\" style=\"display:inline-block;"
                + "background:#3dac78;color:#ffffff;text-decoration:none;font-weight:700;font-size:15px;padding:13px 34px;"
                + "border-radius:10px\">" + label + "</a></div>";
    }
    private static String infoBox(String innerHtml) {
        return "<div style=\"background:#f7f9fc;border:1px solid #e6e9f0;border-radius:10px;padding:12px 14px;"
                + "margin:8px 0 14px;font-size:14px;color:#16382a;line-height:1.7\">" + innerHtml + "</div>";
    }

    /** Khung email HTML chung: header thương hiệu Dididi (xanh), card trắng, footer navy. */
    private String htmlShell(String heading, String innerHtml, Locale loc) {
        return "<!DOCTYPE html><html lang=\"" + loc.getLanguage() + "\"><body style=\"margin:0;padding:0;background:#f1f4f9;"
            + "font-family:Arial,Helvetica,'Segoe UI',sans-serif;color:#1c2536\">"
            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f1f4f9;padding:26px 12px\">"
            + "<tr><td align=\"center\">"
            + "<table role=\"presentation\" width=\"480\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:480px;max-width:100%;"
            + "background:#ffffff;border:1px solid #e6e9f0;border-radius:14px;overflow:hidden\">"
            + "<tr><td style=\"background:#3dac78;padding:20px 28px\">"
            + "<span style=\"color:#ffffff;font-size:22px;font-weight:800;letter-spacing:-.5px\">Dididi</span>"
            + "<span style=\"color:#cce8da;font-size:13px\"> " + m(loc, "email.tagline") + "</span></td></tr>"
            + "<tr><td style=\"padding:28px\">"
            + "<h1 style=\"margin:0 0 18px;font-size:20px;color:#16382a\">" + heading + "</h1>"
            + innerHtml
            + "</td></tr>"
            + "<tr><td style=\"background:#16382a;padding:16px 28px;color:#aeb8c7;font-size:12px;line-height:1.6\">"
            + m(loc, "email.footer") + "</td></tr>"
            + "</table></td></tr></table></body></html>";
    }

    /** Gửi email dạng HTML (kèm bản text thay thế) - vẫn phòng thủ, lỗi chỉ log. */
    private void sendHtml(String to, String subject, String html, String plainAlt) {
        if (!enabled) { log.debug("Mail tat (app.mail.enabled=false), bo qua: {}", subject); return; }
        if (to == null || to.isBlank()) { log.warn("Khong co email nguoi nhan, bo qua: {}", subject); return; }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
            h.setFrom(from);
            h.setTo(to);
            h.setSubject(subject);
            h.setText(plainAlt != null ? plainAlt : "", html);
            mailSender.send(msg);
            log.info("Da gui email HTML -> {} : {}", to, subject);
        } catch (Exception e) {
            log.warn("Gui email HTML that bai (to={}, subject={}): {}", to, subject, e.getMessage());
        }
    }

    private String emailOf(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId).map(User::getEmail).orElse(null);
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static String money(BigDecimal v) { return v == null ? "0" : v.toPlainString(); }
}
