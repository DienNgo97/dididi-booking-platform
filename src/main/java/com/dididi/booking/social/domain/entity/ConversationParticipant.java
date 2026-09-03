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

    /**
     * XOÁ ĐOẠN CHAT — chỉ ở PHÍA NGƯỜI NÀY. Không xoá tin nhắn thật vì lịch sử còn thuộc về người
     * bên kia; ta chỉ ẩn hội thoại khỏi hộp thư của người bấm xoá.
     */
    @Column(name = "hidden_at")
    private java.time.Instant hiddenAt;

    /**
     * Mốc xoá: chỉ hiển thị tin có id LỚN HƠN giá trị này. Nhờ vậy khi người kia nhắn tiếp,
     * hội thoại hiện lại nhưng bắt đầu từ tin mới — không lôi lại đoạn cũ đã xoá.
     */
    @Column(name = "cleared_before_message_id", nullable = false)
    private long clearedBeforeMessageId = 0;

    /** LƯU TRỮ: ẩn khỏi hộp thư chính, xem ở mục "Lưu trữ"; có tin mới thì tự bỏ lưu trữ. */
    @Column(name = "archived_at")
    private java.time.Instant archivedAt;

    /** Đã rời nhóm (chỉ dùng cho hội thoại nhóm) — giữ bản ghi để lịch sử vẫn hiểu được. */
    @Column(name = "left_at")
    private java.time.Instant leftAt;

    /** Chủ nhóm được đổi tên nhóm và mời/xoá thành viên; thành viên thường chỉ mời thêm. */
    @Column(name = "is_owner", nullable = false)
    private boolean owner = false;

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public long getLastReadMessageId() { return lastReadMessageId; }
    public void setLastReadMessageId(long lastReadMessageId) { this.lastReadMessageId = lastReadMessageId; }
    public java.time.Instant getHiddenAt() { return hiddenAt; }
    public void setHiddenAt(java.time.Instant hiddenAt) { this.hiddenAt = hiddenAt; }
    public long getClearedBeforeMessageId() { return clearedBeforeMessageId; }
    public void setClearedBeforeMessageId(long v) { this.clearedBeforeMessageId = v; }
    public java.time.Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(java.time.Instant archivedAt) { this.archivedAt = archivedAt; }
    public java.time.Instant getLeftAt() { return leftAt; }
    public void setLeftAt(java.time.Instant leftAt) { this.leftAt = leftAt; }
    public boolean isOwner() { return owner; }
    public void setOwner(boolean owner) { this.owner = owner; }
}
