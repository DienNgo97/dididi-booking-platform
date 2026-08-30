package com.dididi.booking.social.api.dto;

/**
 * 1 dong trong hop thu. Voi hoi thoai 1-1: 'other' la nguoi kia.
 * Voi nhom: 'other' la mot chu the gia mang ten nhom (de template dung chung 1 khoi hien thi).
 */
public class ConversationView {

    private final Long id;
    private final ActorView other;
    private final String preview;
    private final long lastMessageAtMs;
    private final int unread;
    private final String url;
    /** Hội thoại nhóm hay 1-1 — UI đổi nhãn nút (Rời nhóm / Xoá đoạn chat). */
    private final boolean group;
    /** Đang nằm trong mục Lưu trữ. */
    private final boolean archived;
    /** Số thành viên còn trong nhóm (1-1 luôn là 2). */
    private final int memberCount;

    public ConversationView(Long id, ActorView other, String preview, long lastMessageAtMs, int unread, String url) {
        this(id, other, preview, lastMessageAtMs, unread, url, false, false, 2);
    }

    public ConversationView(Long id, ActorView other, String preview, long lastMessageAtMs, int unread, String url,
                            boolean group, boolean archived, int memberCount) {
        this.id = id;
        this.other = other;
        this.preview = preview;
        this.lastMessageAtMs = lastMessageAtMs;
        this.unread = unread;
        this.url = url;
        this.group = group;
        this.archived = archived;
        this.memberCount = memberCount;
    }

    public Long getId() { return id; }
    public ActorView getOther() { return other; }
    public String getPreview() { return preview; }
    public long getLastMessageAtMs() { return lastMessageAtMs; }
    public int getUnread() { return unread; }
    public String getUrl() { return url; }
    public boolean isGroup() { return group; }
    public boolean isArchived() { return archived; }
    public int getMemberCount() { return memberCount; }
}
