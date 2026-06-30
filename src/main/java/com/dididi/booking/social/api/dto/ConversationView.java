package com.dididi.booking.social.api.dto;

/** 1 dong trong hop thu: hoi thoai 1-1 voi nguoi 'other', xem truoc tin cuoi + so chua doc. */
public class ConversationView {

    private final Long id;
    private final ActorView other;
    private final String preview;
    private final long lastMessageAtMs;
    private final int unread;
    private final String url;

    public ConversationView(Long id, ActorView other, String preview, long lastMessageAtMs, int unread, String url) {
        this.id = id;
        this.other = other;
        this.preview = preview;
        this.lastMessageAtMs = lastMessageAtMs;
        this.unread = unread;
        this.url = url;
    }

    public Long getId() { return id; }
    public ActorView getOther() { return other; }
    public String getPreview() { return preview; }
    public long getLastMessageAtMs() { return lastMessageAtMs; }
    public int getUnread() { return unread; }
    public String getUrl() { return url; }
}
