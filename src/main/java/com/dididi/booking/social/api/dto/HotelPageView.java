package com.dididi.booking.social.api.dto;

/** Trang khach san (page) trong Cong dong. canPost = nguoi xem co quyen dang duoi danh nghia KS. */
public class HotelPageView {

    private final Long hotelId;
    private final String name;
    private final String city;
    private final Integer star;
    private final String avatarUrl;
    private final long postsCount;
    private final long followersCount;
    private final String followState;
    private final boolean canPost;
    private final String hotelUrl;

    public HotelPageView(Long hotelId, String name, String city, Integer star, String avatarUrl,
                         long postsCount, long followersCount, String followState, boolean canPost, String hotelUrl) {
        this.hotelId = hotelId;
        this.name = name;
        this.city = city;
        this.star = star;
        this.avatarUrl = avatarUrl;
        this.postsCount = postsCount;
        this.followersCount = followersCount;
        this.followState = followState;
        this.canPost = canPost;
        this.hotelUrl = hotelUrl;
    }

    public Long getHotelId() { return hotelId; }
    public String getName() { return name; }
    public String getCity() { return city; }
    public Integer getStar() { return star; }
    public String getAvatarUrl() { return avatarUrl; }
    public long getPostsCount() { return postsCount; }
    public long getFollowersCount() { return followersCount; }
    public String getFollowState() { return followState; }
    public boolean isCanPost() { return canPost; }
    public String getHotelUrl() { return hotelUrl; }
}
