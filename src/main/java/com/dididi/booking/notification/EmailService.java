package com.dididi.booking.notification;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Gui email thong bao (Phase Dot1 #2) qua SMTP (Gmail).
 * NGUYEN TAC: gui PHONG THU - moi loi gui mail chi log canh bao, KHONG nem ra
 * lam hong luong dat/thanh toan/duyet. Co the tat bang app.mail.enabled=false.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;

    @Value("${app.mail.enabled:true}")
    private boolean enabled;

    @Value("${app.mail.from:Dididi <no-reply@dididi.local>}")
    private String from;

    public EmailService(JavaMailSender mailSender, UserRepository userRepository) {
        this.mailSender = mailSender;
        this.userRepository = userRepository;
    }

    // ---------------- public API ----------------

    @Async
    public void sendBookingConfirmed(Booking b) {
        if (b == null) return;
        String to = emailOf(b.getUserId());
        String body = "Xin chào,\n\n"
                + "Đơn " + b.getPublicCode() + " (" + safe(b.getTitle()) + ") đã được XÁC NHẬN.\n"
                + "Số tiền: " + money(b.getAmount()) + " " + b.getCurrency() + "\n\n"
                + "Cảm ơn bạn đã sử dụng Dididi.";
        send(to, "[Dididi] Xác nhận đơn " + b.getPublicCode(), body);
    }

    @Async
    public void sendRefunded(Booking b, BigDecimal amount) {
        if (b == null) return;
        String to = emailOf(b.getUserId());
        String body = "Xin chào,\n\n"
                + "Đơn " + b.getPublicCode() + " đã được HOÀN TIỀN " + money(amount) + " " + b.getCurrency() + ".\n"
                + "Đơn đã chuyển sang trạng thái huỷ.\n\n"
                + "Dididi.";
        send(to, "[Dididi] Hoàn tiền đơn " + b.getPublicCode(), body);
    }

    @Async
    public void sendVendorApproved(Long userId, String hotelName) {
        String to = emailOf(userId);
        String body = "Xin chào,\n\n"
                + "Tài khoản đối tác (vendor) của bạn đã được DUYỆT.\n"
                + (hotelName != null ? "Khách sạn: " + hotelName + "\n" : "")
                + "Bạn có thể đăng nhập để quản lý phòng và tồn kho.\n\n"
                + "Dididi.";
        send(to, "[Dididi] Tài khoản vendor đã được duyệt", body);
    }

    @Async
    public void sendVendorRejected(Long userId) {
        String to = emailOf(userId);
        String body = "Xin chào,\n\n"
                + "Rất tiếc, đăng ký đối tác (vendor) của bạn chưa được duyệt.\n"
                + "Vui lòng liên hệ quản trị viên để biết thêm chi tiết.\n\n"
                + "Dididi.";
        send(to, "[Dididi] Kết quả đăng ký vendor", body);
    }

    // ---------------- helpers ----------------

    @org.springframework.scheduling.annotation.Async
    public void sendCompanyInvite(String toEmail, String companyName, String acceptUrl) {
        String body = "Xin chào,\n\nBạn được mời tham gia công ty \"" + companyName
                + "\" trên Dididi để đặt phòng/vé bằng ngân sách công ty.\n"
                + "Nhấn vào liên kết sau để chấp nhận lời mời (đăng nhập bằng đúng email này):\n" + acceptUrl + "\n\n"
                + "Liên kết có hiệu lực trong 7 ngày.\n\nDididi";
        send(toEmail, "[Dididi] Lời mời tham gia công ty " + companyName, body);
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

    private String emailOf(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId).map(User::getEmail).orElse(null);
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static String money(BigDecimal v) { return v == null ? "0" : v.toPlainString(); }
}
