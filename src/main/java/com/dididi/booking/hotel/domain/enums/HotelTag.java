package com.dididi.booking.hotel.domain.enums;

/** Đặc điểm nổi bật của khách sạn — tín hiệu mạnh cho recommendation theo nhu cầu. */
public enum HotelTag {
    BEACHFRONT("Sát biển"),
    CITY_CENTER("Trung tâm"),
    SEA_VIEW("View biển"),
    FAMILY_FRIENDLY("Hợp gia đình"),
    BUSINESS("Phù hợp công tác"),
    ROMANTIC("Lãng mạn"),
    NEAR_AIRPORT("Gần sân bay"),
    QUIET("Yên tĩnh"),
    LUXURY("Sang trọng"),
    BUDGET("Tiết kiệm");

    private final String viName;
    HotelTag(String viName) { this.viName = viName; }
    public String getViName() { return viName; }
}
