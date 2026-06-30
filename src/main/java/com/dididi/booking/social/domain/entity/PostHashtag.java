package com.dididi.booking.social.domain.entity;

import com.dididi.booking.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Lien ket bai <-> hashtag. */
@Entity
@Table(name = "social_post_hashtags",
        uniqueConstraints = @UniqueConstraint(name = "uk_post_hashtag", columnNames = {"post_id", "hashtag_id"}),
        indexes = @Index(name = "idx_posthashtag_tag", columnList = "hashtag_id,id"))
public class PostHashtag extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "hashtag_id", nullable = false)
    private Long hashtagId;

    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public Long getHashtagId() { return hashtagId; }
    public void setHashtagId(Long hashtagId) { this.hashtagId = hashtagId; }
}
