package com.dididi.booking.social.api.dto;

/** Thong bao da do san de hien thi. message = cau tieng Viet, url = noi can mo khi bam. */
public class NotificationView {

    private final Long id;
    private final ActorView actor;
    private final String type;
    private final String message;
    private final String url;
    private final boolean read;
    private final long createdAtMs;

    public NotificationView(Long id, ActorView actor, String type, String message, String url,
                            boolean read, long createdAtMs) {
        this.id = id;
        this.actor = actor;
        this.type = type;
        this.message = message;
        this.url = url;
        this.read = read;
        this.createdAtMs = createdAtMs;
    }

    public Long getId() { return id; }
    public ActorView getActor() { return actor; }
    public String getType() { return type; }
    public String getMessage() { return message; }
    public String getUrl() { return url; }
    public boolean isRead() { return read; }
    public long getCreatedAtMs() { return createdAtMs; }
}
