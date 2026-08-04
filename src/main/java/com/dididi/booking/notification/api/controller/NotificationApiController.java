package com.dididi.booking.notification.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.notification.api.dto.NotifFeedItem;
import com.dididi.booking.notification.service.NotificationFeedService;
import com.dididi.booking.notification.service.UserNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Trung tâm thông báo TỔNG cho Flutter (JWT: principal = userId).
 * Gộp 3 nguồn (nền tảng: đơn/thanh toán/huỷ/loyalty + Cộng đồng + tin nhắn) — dùng lại
 * NotificationFeedService như bản web /notifications.
 */
@Tag(name = "Notifications (khách)", description = "Trung tâm thông báo tổng hợp")
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationApiController {

    private final NotificationFeedService feedService;
    private final UserNotificationService userNotificationService;

    public NotificationApiController(NotificationFeedService feedService,
                                     UserNotificationService userNotificationService) {
        this.feedService = feedService;
        this.userNotificationService = userNotificationService;
    }

    private Long uid(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        return Long.valueOf(auth.getName());
    }

    @Operation(summary = "Dòng thời gian thông báo tổng (đơn/thanh toán/huỷ + cộng đồng + tin nhắn)")
    @GetMapping("/feed")
    public ApiResponse<List<NotifFeedItem>> feed(Authentication auth) {
        return ApiResponse.ok(feedService.recent(uid(auth), 50));
    }

    @Operation(summary = "Tổng số thông báo chưa đọc (badge chuông)")
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Object>> unreadCount(Authentication auth) {
        return ApiResponse.ok(Map.of("count", feedService.totalUnread(uid(auth))));
    }

    @Operation(summary = "Đánh dấu đã đọc các thông báo nền tảng (đơn/thanh toán/huỷ/loyalty)")
    @PostMapping("/read")
    public ApiResponse<Void> markRead(Authentication auth) {
        userNotificationService.markAllRead(uid(auth));
        return ApiResponse.ok(null, "OK");
    }
}
