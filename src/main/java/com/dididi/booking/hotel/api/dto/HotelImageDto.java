package com.dididi.booking.hotel.api.dto;

import com.dididi.booking.hotel.domain.entity.HotelImage;

/** url = endpoint app phuc vu anh (cong khai), khong lo objectKey MinIO ra ngoai. */
public record HotelImageDto(Long id, Long hotelId, int sortOrder, String url) {

    public static HotelImageDto from(HotelImage i) {
        return new HotelImageDto(i.getId(), i.getHotelId(), i.getSortOrder(),
                "/api/v1/hotels/" + i.getHotelId() + "/images/" + i.getId());
    }
}
