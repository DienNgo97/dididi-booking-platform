package com.dididi.booking.social.domain.entity;

import com.dididi.booking.common.domain.BaseEntity;
import com.dididi.booking.social.domain.enums.MessageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** 1 tin nhan trong hoi thoai. */
@Entity
@Table(name = "social_messages",
        indexes = @Index(name = "idx_message_conv", columnList = "conversation_id,id"))
public class Message extends BaseEntity {

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private MessageType type = MessageType.TEXT;

    @Column(length = 2000)
    private String content;

    /** Anh dinh kem (MinIO object key) khi type = IMAGE. */
    @Column(name = "attachment_key", length = 300)
    private String attachmentKey;

    @Column(name = "content_type", length = 100)
    private String contentType;

    /** Bai duoc chia se khi type = POST_SHARE. */
    @Column(name = "shared_post_id")
    private Long sharedPostId;

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getAttachmentKey() { return attachmentKey; }
    public void setAttachmentKey(String attachmentKey) { this.attachmentKey = attachmentKey; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getSharedPostId() { return sharedPostId; }
    public void setSharedPostId(Long sharedPostId) { this.sharedPostId = sharedPostId; }
}
