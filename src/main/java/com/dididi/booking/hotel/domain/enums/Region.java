package com.dididi.booking.hotel.domain.enums;

/** Vùng miền — hỗ trợ gợi ý theo khu vực. */
public enum Region {
    NORTH("Miền Bắc"),
    CENTRAL("Miền Trung"),
    SOUTH("Miền Nam");

    private final String viName;
    Region(String viName) { this.viName = viName; }
    public String getViName() { return viName; }
}
