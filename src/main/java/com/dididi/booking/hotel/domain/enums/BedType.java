package com.dididi.booking.hotel.domain.enums;

/** Loại giường của hạng phòng — phục vụ lọc & gợi ý theo nhu cầu. */
public enum BedType {
    SINGLE("Giường đơn"),
    TWIN("2 giường đơn"),
    DOUBLE("Giường đôi"),
    QUEEN("Giường Queen"),
    KING("Giường King");

    private final String viName;
    BedType(String viName) { this.viName = viName; }
    public String getViName() { return viName; }
}
