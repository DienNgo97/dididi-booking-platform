package com.dididi.booking.notification.web;

import com.dididi.booking.notification.service.NotificationFeedService;
import com.dididi.booking.web.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Đưa số thông báo chưa đọc (tổng) ra MỌI trang SSR để hiển thị badge chuông trên header.
 * Chỉ áp dụng cho @Controller (Thymeleaf), giống GlobalNavAdvice.
 */
@ControllerAdvice(annotations = Controller.class)
public class NotificationModelAdvice {

    private final CurrentUser currentUser;
    private final NotificationFeedService feedService;

    public NotificationModelAdvice(CurrentUser currentUser, NotificationFeedService feedService) {
        this.currentUser = currentUser;
        this.feedService = feedService;
    }

    @ModelAttribute("notifUnread")
    public long notifUnread(Authentication auth) {
        Long uid = currentUser.idOrNull(auth);
        return uid == null ? 0 : feedService.totalUnread(uid);
    }
}
