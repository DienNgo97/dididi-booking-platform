package com.dididi.booking.social.api.dto;

/** Trang ca nhan da do san. followState = SELF | ACTIVE | PENDING | NONE. Class de Thymeleaf doc qua getter. */
public class ProfileView {

    private final Long userId;
    private final String handle;
    private final String displayName;
    private final String bio;
    private final String avatarUrl;
    private final String coverUrl;
    private final String link;
    private final boolean privateAccount;
    private final int postsCount;
    private final long followersCount;
    private final long followingCount;
    private final boolean owner;
    private final String followState;
    private final boolean canViewPosts;

    public ProfileView(Long userId, String handle, String displayName, String bio, String avatarUrl, String coverUrl,
                       String link, boolean privateAccount, int postsCount, long followersCount, long followingCount,
                       boolean owner, String followState, boolean canViewPosts) {
        this.userId = userId;
        this.handle = handle;
        this.displayName = displayName;
        this.bio = bio;
        this.avatarUrl = avatarUrl;
        this.coverUrl = coverUrl;
        this.link = link;
        this.privateAccount = privateAccount;
        this.postsCount = postsCount;
        this.followersCount = followersCount;
        this.followingCount = followingCount;
        this.owner = owner;
        this.followState = followState;
        this.canViewPosts = canViewPosts;
    }

    public Long getUserId() { return userId; }
    public String getHandle() { return handle; }
    public String getDisplayName() { return displayName; }
    public String getBio() { return bio; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getCoverUrl() { return coverUrl; }
    public String getLink() { return link; }
    public boolean isPrivateAccount() { return privateAccount; }
    public int getPostsCount() { return postsCount; }
    public long getFollowersCount() { return followersCount; }
    public long getFollowingCount() { return followingCount; }
    public boolean isOwner() { return owner; }
    public String getFollowState() { return followState; }
    public boolean isCanViewPosts() { return canViewPosts; }
}
