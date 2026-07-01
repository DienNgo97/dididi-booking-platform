package com.dididi.booking.notification.web;

import com.dididi.booking.notification.api.dto.NotifFeedItem;
import com.dididi.booking.notification.service.NotificationFeedService;
import com.dididi.booking.notification.service.UserNotificationService;
import com.dididi.booking.web.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;

/**
 * Trung tâm thông báo tổng (đặt phòng/thanh toán/huỷ + Cộng đồng + tin nhắn).
 * - GET  /notifications         : trang đầy đủ (đánh dấu thông báo nền tảng đã đọc khi mở)
 * - GET  /notifications/recent  : JSON cho dropdown trên header
 * - POST /notifications/read    : đánh dấu tất cả thông báo nền tảng đã đọc
 */
@Controller
public class NotificationWebController {

    private final CurrentUser currentUser;
    private final NotificationFeedService feedService;
    private final UserNotificationService userNotifications;

    public NotificationWebController(CurrentUser currentUser, NotificationFeedService feedService,
                                     UserNotificationService userNotifications) {
        this.currentUser = currentUser;
        this.feedService = feedService;
        this.userNotifications = userNotifications;
    }

    @GetMapping("/notifications")
    public String page(Authentication auth, Model model) {
        Long uid = currentUser.id(auth);
        model.addAttribute("items", feedService.recent(uid, 50));
        // Mở trang = đã xem -> đánh dấu thông báo nền tảng đã đọc (social/DM xử lý ở /community).
        userNotifications.markAllRead(uid);
        return "notifications";
    }

    @GetMapping("/notifications/recent")
    @ResponseBody
    public List<NotifFeedItem> recent(Authentication auth) {
        Long uid = currentUser.idOrNull(auth);
        return feedService.recent(uid, 12);
    }

    @PostMapping("/notifications/read")
    public RedirectView markRead(Authentication auth) {
        userNotifications.markAllRead(currentUser.id(auth));
        return new RedirectView("/notifications");
    }
}
