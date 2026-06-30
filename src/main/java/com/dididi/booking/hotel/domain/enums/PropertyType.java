package com.dididi.booking.hotel.domain.enums;

/** Loại hình lưu trú — dùng để lọc & gợi ý (recommendation) theo nhu cầu khách. */
public enum PropertyType {
    HOTEL("Khách sạn"),
    GUESTHOUSE("Nhà nghỉ"),
    HOMESTAY("Homestay"),
    RESORT("Resort"),
    VILLA("Villa"),
    APARTMENT("Căn hộ");

    private final String viName;
    PropertyType(String viName) { this.viName = viName; }
    public String getViName() { return viName; }
}
