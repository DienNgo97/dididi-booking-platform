package com.dididi.booking.social.domain.entity;

import com.dididi.booking.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Bai da luu cua 1 user. */
@Entity
@Table(name = "social_bookmarks",
        uniqueConstraints = @UniqueConstraint(name = "uk_bookmark_one", columnNames = {"user_id", "post_id"}),
        indexes = @Index(name = "idx_bookmark_user", columnList = "user_id,id"))
public class Bookmark extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
}
