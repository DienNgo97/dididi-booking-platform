package com.dididi.booking.social.api.dto;

/**
 * Thong tin hien thi cua mot chu the (tac gia/follow): ca nhan hoac trang khach san.
 * type = "USER" | "HOTEL". avatarUrl null => UI dung chu cai dau (user) hoac icon toa nha (hotel).
 * Class (khong phai record) de Thymeleaf doc duoc property qua getter chuan.
 */
public class ActorView {

    private final String type;
    private final Long id;
    private final String name;
    private final String handle;
    private final String avatarUrl;
    private final String profileUrl;

    public ActorView(String type, Long id, String name, String handle, String avatarUrl, String profileUrl) {
        this.type = type;
        this.id = id;
        this.name = name;
        this.handle = handle;
        this.avatarUrl = avatarUrl;
        this.profileUrl = profileUrl;
    }

    public String getType() { return type; }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getHandle() { return handle; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getProfileUrl() { return profileUrl; }
    public boolean isHotel() { return "HOTEL".equals(type); }
}
