package com.dididi.booking.social.api.dto;

import java.util.List;

/** Bai dang da "do" san de hien thi tren web + tra ve API. Class de Thymeleaf doc qua getter. */
public class PostView {

    private final Long id;
    private final ActorView actor;
    private final String caption;
    private final String captionHtml;
    private final String type;
    private final long createdAtMs;
    private final List<MediaView> media;
    private final Long hotelId;
    private final String hotelName;
    private final String hotelUrl;
    private final String placeName;
    private final Double lat;
    private final Double lng;
    private final int likeCount;
    private final int commentCount;
    private final boolean liked;
    private final boolean canDelete;
    private final String detailUrl;

    public PostView(Long id, ActorView actor, String caption, String captionHtml, String type, long createdAtMs,
                    List<MediaView> media, Long hotelId, String hotelName, String hotelUrl, String placeName,
                    Double lat, Double lng, int likeCount, int commentCount, boolean liked, boolean canDelete,
                    String detailUrl) {
        this.id = id;
        this.actor = actor;
        this.caption = caption;
        this.captionHtml = captionHtml;
        this.type = type;
        this.createdAtMs = createdAtMs;
        this.media = media;
        this.hotelId = hotelId;
        this.hotelName = hotelName;
        this.hotelUrl = hotelUrl;
        this.placeName = placeName;
        this.lat = lat;
        this.lng = lng;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.liked = liked;
        this.canDelete = canDelete;
        this.detailUrl = detailUrl;
    }

    public Long getId() { return id; }
    public ActorView getActor() { return actor; }
    public String getCaption() { return caption; }
    public String getCaptionHtml() { return captionHtml; }
    public String getType() { return type; }
    public long getCreatedAtMs() { return createdAtMs; }
    public List<MediaView> getMedia() { return media; }
    public Long getHotelId() { return hotelId; }
    public String getHotelName() { return hotelName; }
    public String getHotelUrl() { return hotelUrl; }
    public String getPlaceName() { return placeName; }
    public Double getLat() { return lat; }
    public Double getLng() { return lng; }
    public int getLikeCount() { return likeCount; }
    public int getCommentCount() { return commentCount; }
    public boolean isLiked() { return liked; }
    public boolean isCanDelete() { return canDelete; }
    public String getDetailUrl() { return detailUrl; }

    // ----- P2: gan sau khi tao bang setter -----
    private int repostCount;
    private boolean bookmarked;
    private boolean reposted;
    private boolean repost;
    private PostView original;

    public int getRepostCount() { return repostCount; }
    public void setRepostCount(int repostCount) { this.repostCount = repostCount; }
    public boolean isBookmarked() { return bookmarked; }
    public void setBookmarked(boolean bookmarked) { this.bookmarked = bookmarked; }
    public boolean isReposted() { return reposted; }
    public void setReposted(boolean reposted) { this.reposted = reposted; }
    public boolean isRepost() { return repost; }
    public void setRepost(boolean repost) { this.repost = repost; }
    public PostView getOriginal() { return original; }
    public void setOriginal(PostView original) { this.original = original; }

    public boolean hasMedia() { return media != null && !media.isEmpty(); }
    public boolean hasGeo() { return lat != null && lng != null; }
}
