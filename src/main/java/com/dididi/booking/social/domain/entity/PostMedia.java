package com.dididi.booking.social.domain.entity;

import com.dididi.booking.common.domain.BaseEntity;
import com.dididi.booking.social.domain.enums.MediaType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** Anh/video dinh kem 1 bai dang. Luu tren MinIO (object_key). */
@Entity
@Table(name = "social_post_media", indexes = {
        @Index(name = "idx_postmedia_post", columnList = "post_id,sort_order")
})
public class PostMedia extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 8)
    private MediaType mediaType = MediaType.IMAGE;

    @Column(name = "object_key", nullable = false, length = 300)
    private String objectKey;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column private Integer width;
    @Column private Integer height;

    /** Thoi luong video (giay), null voi anh. */
    @Column(name = "duration_sec")
    private Integer durationSec;

    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public MediaType getMediaType() { return mediaType; }
    public void setMediaType(MediaType mediaType) { this.mediaType = mediaType; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }
    public Integer getDurationSec() { return durationSec; }
    public void setDurationSec(Integer durationSec) { this.durationSec = durationSec; }

    public boolean isVideo() { return mediaType == MediaType.VIDEO; }
}
