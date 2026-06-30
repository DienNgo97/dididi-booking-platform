package com.dididi.booking.social.domain.entity;

import com.dididi.booking.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Thanh vien cua 1 hoi thoai. lastReadMessageId = tin nhan cuoi da doc (de dem chua doc). */
@Entity
@Table(name = "social_conversation_participants",
        uniqueConstraints = @UniqueConstraint(name = "uk_conv_participant", columnNames = {"conversation_id", "user_id"}),
        indexes = @Index(name = "idx_participant_user", columnList = "user_id"))
public class ConversationParticipant extends BaseEntity {

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "last_read_message_id", nullable = false)
    private long lastReadMessageId = 0;

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public long getLastReadMessageId() { return lastReadMessageId; }
    public void setLastReadMessageId(long lastReadMessageId) { this.lastReadMessageId = lastReadMessageId; }
}
