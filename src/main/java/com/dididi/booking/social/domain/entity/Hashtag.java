package com.dididi.booking.social.domain.entity;

import com.dididi.booking.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Hashtag (khong dau '#', luu chu thuong). postCount = so bai dang dung (de xep trending). */
@Entity
@Table(name = "social_hashtags",
        uniqueConstraints = @UniqueConstraint(name = "uk_hashtag_tag", columnNames = "tag"))
public class Hashtag extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String tag;

    @Column(name = "post_count", nullable = false)
    private int postCount = 0;

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public int getPostCount() { return postCount; }
    public void setPostCount(int postCount) { this.postCount = postCount; }
}
