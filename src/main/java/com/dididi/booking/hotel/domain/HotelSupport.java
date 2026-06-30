package com.dididi.booking.hotel.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Tiện ích dùng chung cho hotel: ghép chuỗi địa chỉ + parse enum an toàn (admin & vendor). */
public final class HotelSupport {

    private HotelSupport() {}

    /** Ghép địa chỉ hiển thị từ các thành phần tách nhỏ (bỏ qua phần trống). */
    public static String composeAddress(String house, String street, String ward,
                                        String district, String province, String city) {
        List<String> parts = new ArrayList<>();
        String line1 = ((house == null ? "" : house.trim()) + " " + (street == null ? "" : street.trim())).trim();
        if (!line1.isBlank()) parts.add(line1);
        addIf(parts, ward);
        addIf(parts, district);
        String prov = (province != null && !province.isBlank()) ? province : city;
        addIf(parts, prov);
        return String.join(", ", parts);
    }

    private static void addIf(List<String> parts, String v) {
        if (v != null && !v.isBlank()) parts.add(v.trim());
    }

    /** Parse 1 enum theo tên (không phân biệt hoa/thường); sai/rỗng -> null. */
    public static <E extends Enum<E>> E parseEnum(Class<E> cls, String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return Enum.valueOf(cls, v.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Parse danh sách enum (bỏ giá trị sai), giữ thứ tự. */
    public static <E extends Enum<E>> Set<E> parseEnumSet(Class<E> cls, Collection<String> vs) {
        Set<E> out = new LinkedHashSet<>();
        if (vs != null) {
            for (String v : vs) {
                E e = parseEnum(cls, v);
                if (e != null) out.add(e);
            }
        }
        return out;
    }
}
