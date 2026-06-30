package com.dididi.booking.hotel.domain.enums;

/** Hướng nhìn của phòng — tín hiệu gợi ý (view biển, view thành phố...). */
public enum RoomView {
    NONE("Không có"),
    CITY("Hướng thành phố"),
    SEA("Hướng biển"),
    GARDEN("Hướng vườn"),
    POOL("Hướng hồ bơi"),
    MOUNTAIN("Hướng núi");

    private final String viName;
    RoomView(String viName) { this.viName = viName; }
    public String getViName() { return viName; }
}
