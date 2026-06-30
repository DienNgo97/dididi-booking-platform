package com.dididi.booking.social.domain.entity;

import com.dididi.booking.common.domain.BaseEntity;
import com.dididi.booking.social.domain.enums.ProfileVisibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Ho so mang xa hoi cua 1 user (1-1 voi users). Tao lazy lan dau user vao Cong dong.
 * handle dung cho URL /community/u/{handle} va @mention.
 */
@Entity
@Table(name = "social_profiles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_social_profile_user", columnNames = "user_id"),
        @UniqueConstraint(name = "uk_social_profile_handle", columnNames = "handle")
})
public class SocialProfile extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 30)
    private String handle;

    @Column(name = "display_name", length = 120)
    private String displayName;

    @Column(length = 500)
    private String bio;

    /** Object key anh dai dien tren MinIO (null = dung chu cai dau). */
    @Column(name = "avatar_key", length = 300)
    private String avatarKey;

    /** Object key anh bia. */
    @Column(name = "cover_key", length = 300)
    private String coverKey;

    @Column(length = 200)
    private String link;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProfileVisibility visibility = ProfileVisibility.PUBLIC;

    @Column(name = "posts_count", nullable = false)
    private int postsCount = 0;

    @Column(name = "followers_count", nullable = false)
    private int followersCount = 0;

    @Column(name = "following_count", nullable = false)
    private int followingCount = 0;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getHandle() { return handle; }
    public void setHandle(String handle) { this.handle = handle; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getAvatarKey() { return avatarKey; }
    public void setAvatarKey(String avatarKey) { this.avatarKey = avatarKey; }
    public String getCoverKey() { return coverKey; }
    public void setCoverKey(String coverKey) { this.coverKey = coverKey; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
    public ProfileVisibility getVisibility() { return visibility; }
    public void setVisibility(ProfileVisibility visibility) { this.visibility = visibility; }
    public int getPostsCount() { return postsCount; }
    public void setPostsCount(int postsCount) { this.postsCount = postsCount; }
    public int getFollowersCount() { return followersCount; }
    public void setFollowersCount(int followersCount) { this.followersCount = followersCount; }
    public int getFollowingCount() { return followingCount; }
    public void setFollowingCount(int followingCount) { this.followingCount = followingCount; }

    public boolean isPrivate() { return visibility == ProfileVisibility.PRIVATE; }
}
