package com.dididi.booking.social.service;

import com.dididi.booking.social.domain.entity.Notification;
import com.dididi.booking.social.domain.enums.NotificationType;
import com.dididi.booking.social.repository.NotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Tao + doc thong bao. La (khong phu thuoc service khac) de tranh vong lap. */
@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /** Tao 1 thong bao. Bo qua neu tu thong bao cho chinh minh. */
    public void create(Long recipientUserId, Long actorUserId, NotificationType type, Long postId, Long commentId) {
        if (recipientUserId == null || actorUserId == null || recipientUserId.equals(actorUserId)) {
            return;
        }
        Notification n = new Notification();
        n.setRecipientUserId(recipientUserId);
        n.setActorUserId(actorUserId);
        n.setType(type);
        n.setPostId(postId);
        n.setCommentId(commentId);
        notificationRepository.save(n);
    }

    @Transactional(readOnly = true)
    public List<Notification> list(Long userId, int limit) {
        return notificationRepository.findByRecipientUserIdOrderByIdDesc(userId, PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByRecipientUserIdAndReadFalse(userId);
    }

    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId);
    }
}
