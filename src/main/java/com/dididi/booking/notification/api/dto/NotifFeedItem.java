package com.dididi.booking.notification.api.dto;

/**
 * Một dòng trong trung tâm thông báo tổng (đã gộp từ 3 nguồn: nền tảng, Cộng đồng, tin nhắn).
 * Phẳng để render thẳng (Thymeleaf hoặc JSON cho dropdown).
 */
public class NotifFeedItem {

    private final String icon;        // id symbol icons.html
    private final String category;    // BOOKING / PAYMENT / CANCEL / LOYALTY / REVIEW / INVITE / GROUP / SOCIAL / DM
    private final String title;       // dòng đậm
    private final String text;        // mô tả
    private final String url;         // nơi mở khi bấm
    private final boolean read;
    private final long createdAtMs;

    public NotifFeedItem(String icon, String category, String title, String text,
                         String url, boolean read, long createdAtMs) {
        this.icon = icon;
        this.category = category;
        this.title = title;
        this.text = text;
        this.url = url;
        this.read = read;
        this.createdAtMs = createdAtMs;
    }

    public String getIcon() { return icon; }
    public String getCategory() { return category; }
    public String getTitle() { return title; }
    public String getText() { return text; }
    public String getUrl() { return url; }
    public boolean isRead() { return read; }
    public long getCreatedAtMs() { return createdAtMs; }
}
