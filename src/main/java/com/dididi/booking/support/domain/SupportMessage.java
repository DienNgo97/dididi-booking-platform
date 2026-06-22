package com.dididi.booking.support.domain;

import com.dididi.booking.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 1 tin nhắn trong hội thoại hỗ trợ — lưu để thống kê & huấn luyện chatbot sau này.
 * Mỗi lượt hỏi-đáp AI sinh 2 dòng (USER + BOT); phần tổng đài sinh thêm USER/AGENT/SYSTEM.
 */
@Entity
@Table(name = "support_messages", indexes = {
        @Index(name = "idx_support_conv", columnList = "conversation_id"),
        @Index(name = "idx_support_role", columnList = "role")
})
public class SupportMessage extends BaseEntity {

    /** Gom các tin nhắn cùng 1 phiên chat (UUID do client sinh). */
    @Column(name = "conversation_id", nullable = false, length = 40)
    private String conversationId;

    /** Khách đã đăng nhập (null = khách vãng lai). */
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SupportRole role;

    @Column(nullable = false, length = 2000)
    private String content;

    /** Nguồn câu trả lời cho role=BOT: kb | llm | none. null với role khác. */
    @Column(length = 10)
    private String source;

    /** Lượt này có chuyển/đề nghị tổng đài viên không. */
    @Column(nullable = false)
    private boolean escalated = false;

    /** Mã đơn liên quan (nếu khách mở chat từ chi tiết đơn). */
    @Column(name = "booking_code", length = 20)
    private String bookingCode;

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public SupportRole getRole() { return role; }
    public void setRole(SupportRole role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public boolean isEscalated() { return escalated; }
    public void setEscalated(boolean escalated) { this.escalated = escalated; }
    public String getBookingCode() { return bookingCode; }
    public void setBookingCode(String bookingCode) { this.bookingCode = bookingCode; }
}
