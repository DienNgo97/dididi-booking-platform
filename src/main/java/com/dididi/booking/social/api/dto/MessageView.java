package com.dididi.booking.social.api.dto;

/** 1 tin nhan da do san. mine = do nguoi xem gui. type = TEXT|IMAGE|POST_SHARE. */
public class MessageView {

    private final Long id;
    private final boolean mine;
    private final ActorView sender;
    private final String type;
    private final String content;
    private final String mediaUrl;
    private final PostView sharedPost;
    private final long createdAtMs;

    public MessageView(Long id, boolean mine, ActorView sender, String type, String content,
                       String mediaUrl, PostView sharedPost, long createdAtMs) {
        this.id = id;
        this.mine = mine;
        this.sender = sender;
        this.type = type;
        this.content = content;
        this.mediaUrl = mediaUrl;
        this.sharedPost = sharedPost;
        this.createdAtMs = createdAtMs;
    }

    public Long getId() { return id; }
    public boolean isMine() { return mine; }
    public ActorView getSender() { return sender; }
    public String getType() { return type; }
    public String getContent() { return content; }
    public String getMediaUrl() { return mediaUrl; }
    public PostView getSharedPost() { return sharedPost; }
    public long getCreatedAtMs() { return createdAtMs; }

    public boolean isImage() { return "IMAGE".equals(type); }
    public boolean isPostShare() { return "POST_SHARE".equals(type); }
}
