package com.dididi.booking.hotel.domain.enums;

/**
 * Tiện ích khách sạn — bảng nhiều-nhiều (hotel_amenities) phục vụ lọc & gợi ý theo nhu cầu.
 * Mỗi tiện ích gắn 1 icon trong bộ icon sprite (templates/fragments/icons.html).
 */
public enum Amenity {
    WIFI("Wifi miễn phí", "wifi"),
    POOL("Hồ bơi", "pool"),
    PARKING("Bãi đỗ xe", "parking"),
    BREAKFAST("Bữa sáng", "coffee"),
    AC("Máy lạnh", "snow"),
    RESTAURANT("Nhà hàng", "utensils"),
    GYM("Phòng gym", "dumbbell"),
    SPA("Spa & massage", "spa"),
    BAR("Quầy bar", "glass"),
    PET_FRIENDLY("Cho phép thú cưng", "paw"),
    AIRPORT_SHUTTLE("Đưa đón sân bay", "shuttle"),
    BEACH_ACCESS("Lối ra biển", "umbrella"),
    FAMILY_ROOM("Phòng gia đình", "users"),
    LAUNDRY("Giặt là", "wash"),
    ELEVATOR("Thang máy", "elevator"),
    KITCHEN("Bếp nấu ăn", "kitchen"),
    RECEPTION_24H("Lễ tân 24/7", "clock"),
    NON_SMOKING("Phòng không hút thuốc", "no-smoke");

    private final String viName;
    private final String icon;
    Amenity(String viName, String icon) { this.viName = viName; this.icon = icon; }
    public String getViName() { return viName; }
    public String getIcon() { return icon; }
}
