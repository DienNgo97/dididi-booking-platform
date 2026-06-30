package com.dididi.booking.hotel.api.dto;

import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.domain.enums.HotelTag;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public record HotelApiDto(
        Long id,
        String name,
        String city,
        String address,
        // dia chi tach nho
        String houseNumber,
        String street,
        String ward,
        String district,
        String province,
        // toa do
        Double lat,
        Double lng,
        String description,
        Integer starRating,
        BigDecimal minPrice,
        String currency,
        Boolean active,
        // phan loai / recommendation
        String propertyType,
        String propertyTypeName,
        String region,
        List<AmenityView> amenities,
        List<String> tags) implements Serializable {

    private static final long serialVersionUID = 2L;

    public record AmenityView(String code, String name, String icon) implements Serializable {}

    public static HotelApiDto from(Hotel h) {
        List<AmenityView> ams = h.getAmenities() == null ? List.of()
                : h.getAmenities().stream().map(a -> new AmenityView(a.name(), a.getViName(), a.getIcon())).toList();
        List<String> tags = h.getTags() == null ? List.of()
                : h.getTags().stream().map(HotelTag::name).toList();
        return new HotelApiDto(
                h.getId(), h.getName(), h.getCity(), h.getAddress(),
                h.getHouseNumber(), h.getStreet(), h.getWard(), h.getDistrict(), h.getProvince(),
                h.getLat(), h.getLng(),
                h.getDescription(), h.getStarRating(), h.getMinPrice(), h.getCurrency(), h.isActive(),
                h.getPropertyType() != null ? h.getPropertyType().name() : null,
                h.getPropertyType() != null ? h.getPropertyType().getViName() : null,
                h.getRegion() != null ? h.getRegion().name() : null,
                ams, tags);
    }
}
