package com.dididi.booking.hotel.domain.enums;

/** Nguồn cung phòng của khách sạn. */
public enum HotelSource {
    /** Lấy từ hệ thống/PMS bên ngoài (vd hotel-pms) — đồng bộ. */
    CHANNEL,
    /** Vendor tự quản phòng trực tiếp trên Dididi. */
    DIRECT
}
