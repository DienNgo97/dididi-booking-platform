package com.dididi.booking.social.domain.entity;

import com.dididi.booking.common.domain.BaseEntity;
import com.dididi.booking.social.domain.enums.ConversationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Hoi thoai tin nhan. Voi DIRECT (1-1): pairKey = "minUserId:maxUserId" (unique) de tranh tao trung.
 */
@Entity
@Table(name = "social_conversations",
        uniqueConstraints = @UniqueConstraint(name = "uk_conversation_pair", columnNames = "pair_key"))
public class Conversation extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private ConversationType type = ConversationType.DIRECT;

    /** Khoa cap (1-1) de dedup; null voi nhom. */
    @Column(name = "pair_key", length = 50)
    private String pairKey;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "last_message_preview", length = 200)
    private String lastMessagePreview;

    public ConversationType getType() { return type; }
    public void setType(ConversationType type) { this.type = type; }
    public String getPairKey() { return pairKey; }
    public void setPairKey(String pairKey) { this.pairKey = pairKey; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Instant getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(Instant lastMessageAt) { this.lastMessageAt = lastMessageAt; }
    public String getLastMessagePreview() { return lastMessagePreview; }
    public void setLastMessagePreview(String lastMessagePreview) { this.lastMessagePreview = lastMessagePreview; }
}
