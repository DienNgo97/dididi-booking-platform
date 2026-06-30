package com.dididi.booking.social.api.dto;

/**
 * The hien thi mot nguoi dung trong danh sach tim kiem / goi y ket noi (trang "Moi nguoi").
 * Class (khong phai record) de Thymeleaf doc property qua getter chuan.
 * followState = "NONE" | "ACTIVE" | "PENDING" | "SELF".
 */
public class UserCardView {

    private final Long userId;
    private final ActorView actor;
    private final String bio;
    private final String followState;
    private final boolean self;

    public UserCardView(Long userId, ActorView actor, String bio, String followState, boolean self) {
        this.userId = userId;
        this.actor = actor;
        this.bio = bio;
        this.followState = followState;
        this.self = self;
    }

    public Long getUserId() { return userId; }
    public ActorView getActor() { return actor; }
    public String getBio() { return bio; }
    public String getFollowState() { return followState; }
    public boolean isSelf() { return self; }
}
