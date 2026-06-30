package com.dididi.booking.social.api.dto;

/** Media hien thi: type = "IMAGE" | "VIDEO"; url tro toi endpoint stream tu MinIO. */
public class MediaView {

    private final Long id;
    private final String mediaType;
    private final String url;
    private final String contentType;

    public MediaView(Long id, String mediaType, String url, String contentType) {
        this.id = id;
        this.mediaType = mediaType;
        this.url = url;
        this.contentType = contentType;
    }

    public Long getId() { return id; }
    public String getMediaType() { return mediaType; }
    public String getUrl() { return url; }
    public String getContentType() { return contentType; }
    public boolean isVideo() { return "VIDEO".equals(mediaType); }
}
