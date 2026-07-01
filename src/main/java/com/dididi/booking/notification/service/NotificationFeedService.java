package com.dididi.booking.notification.service;

import com.dididi.booking.notification.api.dto.NotifFeedItem;
import com.dididi.booking.notification.domain.UserNotification;
import com.dididi.booking.social.api.dto.NotificationView;
import com.dididi.booking.social.domain.entity.Notification;
import com.dididi.booking.social.service.MessagingService;
import com.dididi.booking.social.service.SocialViewService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Gộp 3 nguồn thông báo thành 1 feed cho chuông trang chủ:
 *  1) Thông báo nền tảng (UserNotification) — đặt phòng/thanh toán/huỷ/điểm/đánh giá/nhóm
 *  2) Tương tác Cộng đồng (social Notification) — like/comment/follow/mention...
 *  3) Tin nhắn DM (gộp thành 1 dòng tóm tắt)
 * Aggregator chỉ phục vụ tầng web; không service nghiệp vụ nào phụ thuộc nó (tránh vòng lặp).
 */
@Service
public class NotificationFeedService {

    private final UserNotificationService userNotifications;
    private final com.dididi.booking.social.service.NotificationService socialNotifications;
    private final SocialViewService socialView;
    private final MessagingService messaging;

    public NotificationFeedService(UserNotificationService userNotifications,
                                   com.dididi.booking.social.service.NotificationService socialNotifications,
                                   SocialViewService socialView,
                                   MessagingService messaging) {
        this.userNotifications = userNotifications;
        this.socialNotifications = socialNotifications;
        this.socialView = socialView;
        this.messaging = messaging;
    }

    /** Tổng số chưa đọc cho badge = nền tảng + Cộng đồng + tin nhắn. */
    public long totalUnread(Long uid) {
        if (uid == null) {
            return 0;
        }
        long n = 0;
        try { n += userNotifications.unreadCount(uid); } catch (Exception ignored) { }
        try { n += socialNotifications.unreadCount(uid); } catch (Exception ignored) { }
        try { n += messaging.dmUnreadTotal(uid); } catch (Exception ignored) { }
        return n;
    }

    /** Feed gần đây đã gộp + sắp theo thời gian giảm dần, cắt còn {@code limit} dòng. */
    public List<NotifFeedItem> recent(Long uid, int limit) {
        List<NotifFeedItem> items = new ArrayList<>();
        if (uid == null) {
            return items;
        }

        // 1) Nền tảng
        try {
            for (UserNotification n : userNotifications.list(uid, limit)) {
                long ts = n.getCreatedAt() != null ? n.getCreatedAt().toEpochMilli() : 0L;
                items.add(new NotifFeedItem(
                        n.getType().getIcon(),
                        n.getType().name(),
                        n.getTitle(),
                        n.getBody(),
                        n.getUrl(),
                        n.isRead(),
                        ts));
            }
        } catch (Exception ignored) { }

        // 2) Cộng đồng
        try {
            List<Notification> social = socialNotifications.list(uid, limit);
            for (NotificationView v : socialView.toNotificationViews(social)) {
                String actor = v.getActor() != null ? v.getActor().getName() : "Ai đó";
                items.add(new NotifFeedItem(
                        "ic-heart",
                        "SOCIAL",
                        actor,
                        v.getMessage(),
                        v.getUrl(),
                        v.isRead(),
                        v.getCreatedAtMs()));
            }
        } catch (Exception ignored) { }

        // 3) Tin nhắn (1 dòng tóm tắt nếu có chưa đọc)
        try {
            int dm = messaging.dmUnreadTotal(uid);
            if (dm > 0) {
                items.add(new NotifFeedItem(
                        "ic-send",
                        "DM",
                        "Tin nhắn mới",
                        "Bạn có " + dm + " tin nhắn chưa đọc",
                        "/community/messages",
                        false,
                        System.currentTimeMillis()));
            }
        } catch (Exception ignored) { }

        items.sort(Comparator.comparingLong(NotifFeedItem::getCreatedAtMs).reversed());
        return items.size() > limit ? new ArrayList<>(items.subList(0, limit)) : items;
    }
}
