package com.dididi.booking.notification.service;

import com.dididi.booking.notification.domain.UserNotification;
import com.dididi.booking.notification.domain.UserNotificationType;
import com.dididi.booking.notification.repository.UserNotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Tạo + đọc thông báo nền tảng. Service LÁ (chỉ phụ thuộc repository) để tránh vòng lặp DI.
 * {@link #create} chạy REQUIRES_NEW để lỗi tạo thông báo không kéo đổ giao dịch nghiệp vụ;
 * caller vẫn nên bọc try/catch (xem các call site).
 */
@Service
public class UserNotificationService {

    private final UserNotificationRepository repo;

    public UserNotificationService(UserNotificationRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void create(Long recipientUserId, UserNotificationType type,
                       String title, String body, String url, Long refId) {
        if (recipientUserId == null || type == null) {
            return;
        }
        UserNotification n = new UserNotification();
        n.setRecipientUserId(recipientUserId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setUrl(url);
        n.setRefId(refId);
        repo.save(n);
    }

    @Transactional(readOnly = true)
    public List<UserNotification> list(Long userId, int limit) {
        if (userId == null) {
            return List.of();
        }
        return repo.findByRecipientUserIdOrderByIdDesc(userId, PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return userId == null ? 0 : repo.countByRecipientUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markAllRead(Long userId) {
        if (userId != null) {
            repo.markAllRead(userId);
        }
    }
}
