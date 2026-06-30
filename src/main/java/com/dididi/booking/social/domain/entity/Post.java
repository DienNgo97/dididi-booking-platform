package com.dididi.booking.social.domain.entity;

import com.dididi.booking.common.domain.BaseEntity;
import com.dididi.booking.social.domain.enums.ActorType;
import com.dididi.booking.social.domain.enums.PostStatus;
import com.dididi.booking.social.domain.enums.PostType;
import com.dididi.booking.social.domain.enums.PostVisibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Bai dang. Tac gia hien thi = (actorType, actorId): USER -> users.id, HOTEL -> hotels.id.
 * authorUserId = nguoi thuc su tao bai (khi dang duoi danh nghia khach san, day la user VENDOR).
 * Gan du lich tuy chon: hotelId / bookingId / reviewId / toa do check-in.
 */
@Entity
@Table(name = "social_posts", indexes = {
        @Index(name = "idx_post_actor", columnList = "actor_type,actor_id,id"),
        @Index(name = "idx_post_feed", columnList = "status,visibility,id"),
        @Index(name = "idx_post_author", columnList = "author_user_id"),
        @Index(name = "idx_post_hotel", columnList = "hotel_id")
})
public class Post extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 8)
    private ActorType actorType = ActorType.USER;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "author_user_id", nullable = false)
    private Long authorUserId;

    @Column(length = 2000)
    private String caption;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PostType type = PostType.STANDARD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PostVisibility visibility = PostVisibility.PUBLIC;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PostStatus status = PostStatus.PUBLISHED;

    // ----- Gan du lich (tuy chon) -----
    @Column(name = "hotel_id")
    private Long hotelId;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "review_id")
    private Long reviewId;

    @Column(name = "place_name", length = 200)
    private String placeName;

    @Column(name = "lat")
    private Double lat;

    @Column(name = "lng")
    private Double lng;

    /** Bai goc khi type = REPOST. */
    @Column(name = "origin_post_id")
    private Long originPostId;

    // ----- Dem (denormalized de feed nhanh) -----
    @Column(name = "like_count", nullable = false)
    private int likeCount = 0;

    @Column(name = "comment_count", nullable = false)
    private int commentCount = 0;

    @Column(name = "repost_count", nullable = false)
    private int repostCount = 0;

    public ActorType getActorType() { return actorType; }
    public void setActorType(ActorType actorType) { this.actorType = actorType; }
    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }
    public Long getAuthorUserId() { return authorUserId; }
    public void setAuthorUserId(Long authorUserId) { this.authorUserId = authorUserId; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
    public PostType getType() { return type; }
    public void setType(PostType type) { this.type = type; }
    public PostVisibility getVisibility() { return visibility; }
    public void setVisibility(PostVisibility visibility) { this.visibility = visibility; }
    public PostStatus getStatus() { return status; }
    public void setStatus(PostStatus status) { this.status = status; }
    public Long getHotelId() { return hotelId; }
    public void setHotelId(Long hotelId) { this.hotelId = hotelId; }
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public Long getReviewId() { return reviewId; }
    public void setReviewId(Long reviewId) { this.reviewId = reviewId; }
    public String getPlaceName() { return placeName; }
    public void setPlaceName(String placeName) { this.placeName = placeName; }
    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }
    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }
    public Long getOriginPostId() { return originPostId; }
    public void setOriginPostId(Long originPostId) { this.originPostId = originPostId; }
    public int getLikeCount() { return likeCount; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }
    public int getCommentCount() { return commentCount; }
    public void setCommentCount(int commentCount) { this.commentCount = commentCount; }
    public int getRepostCount() { return repostCount; }
    public void setRepostCount(int repostCount) { this.repostCount = repostCount; }

    public boolean hasGeo() { return lat != null && lng != null; }
}
