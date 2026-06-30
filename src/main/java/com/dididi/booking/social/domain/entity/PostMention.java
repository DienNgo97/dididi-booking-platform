package com.dididi.booking.social.domain.entity;

import com.dididi.booking.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** Nhac ten (@handle) trong 1 bai -> sinh thong bao MENTION. */
@Entity
@Table(name = "social_post_mentions", indexes = {
        @Index(name = "idx_mention_post", columnList = "post_id"),
        @Index(name = "idx_mention_user", columnList = "mentioned_user_id")
})
public class PostMention extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "mentioned_user_id", nullable = false)
    private Long mentionedUserId;

    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public Long getMentionedUserId() { return mentionedUserId; }
    public void setMentionedUserId(Long mentionedUserId) { this.mentionedUserId = mentionedUserId; }
}
