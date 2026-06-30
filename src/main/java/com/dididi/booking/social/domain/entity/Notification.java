package com.dididi.booking.social.domain.entity;

import com.dididi.booking.common.domain.BaseEntity;
import com.dididi.booking.social.domain.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** Thong bao gui toi 1 user. actor = nguoi gay ra; postId/commentId = ngu canh. */
@Entity
@Table(name = "social_notifications", indexes = {
        @Index(name = "idx_notif_recipient", columnList = "recipient_user_id,id"),
        @Index(name = "idx_notif_unread", columnList = "recipient_user_id,is_read")
})
public class Notification extends BaseEntity {

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationType type;

    @Column(name = "post_id")
    private Long postId;

    @Column(name = "comment_id")
    private Long commentId;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    public Long getRecipientUserId() { return recipientUserId; }
    public void setRecipientUserId(Long recipientUserId) { this.recipientUserId = recipientUserId; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }
    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public Long getCommentId() { return commentId; }
    public void setCommentId(Long commentId) { this.commentId = commentId; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
}
