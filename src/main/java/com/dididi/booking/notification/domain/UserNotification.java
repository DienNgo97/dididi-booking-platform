package com.dididi.booking.notification.domain;

import com.dididi.booking.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Thông báo nền tảng gửi tới 1 user (khác với thông báo Cộng đồng ở module social).
 * Lưu sẵn title/body/url để hiển thị trực tiếp, không cần join.
 */
@Entity
@Table(name = "user_notifications", indexes = {
        @Index(name = "idx_unotif_recipient", columnList = "recipient_user_id,id"),
        @Index(name = "idx_unotif_unread", columnList = "recipient_user_id,is_read")
})
public class UserNotification extends BaseEntity {

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserNotificationType type;

    @Column(length = 200)
    private String title;

    @Column(length = 400)
    private String body;

    @Column(length = 300)
    private String url;

    /** Tham chiếu ngữ cảnh (bookingId, reviewId, groupId...). */
    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    public Long getRecipientUserId() { return recipientUserId; }
    public void setRecipientUserId(Long recipientUserId) { this.recipientUserId = recipientUserId; }
    public UserNotificationType getType() { return type; }
    public void setType(UserNotificationType type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Long getRefId() { return refId; }
    public void setRefId(Long refId) { this.refId = refId; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
}
