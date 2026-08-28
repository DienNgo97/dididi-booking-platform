package com.dididi.booking.hotel.domain.entity;

import com.dididi.booking.common.domain.BaseEntity;
import com.dididi.booking.hotel.domain.enums.Amenity;
import com.dididi.booking.hotel.domain.enums.HotelSource;
import com.dididi.booking.hotel.domain.enums.HotelTag;
import com.dididi.booking.hotel.domain.enums.PropertyType;
import com.dididi.booking.hotel.domain.enums.Region;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "hotels", uniqueConstraints = @UniqueConstraint(name = "uk_hotels_external", columnNames = "external_id"))
public class Hotel extends BaseEntity {

    /** ID ben hotel-pms (de upsert khi sync). Null neu tao thu cong / vendor. */
    @Column(name = "external_id")
    private Long externalId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String city;

    /** Chuoi dia chi hien thi day du (ghep tu cac thanh phan tach ben duoi). */
    @Column(length = 300)
    private String address;

    // ----- Dia chi tach nho (phuc vu Maps + recommendation theo khu vuc) -----
    @Column(name = "house_number", length = 50)
    private String houseNumber;

    @Column(length = 150)
    private String street;

    @Column(length = 100)
    private String ward;

    @Column(length = 100)
    private String district;

    @Column(length = 100)
    private String province;

    // ----- Toa do (Google Maps / geo search) -----
    @Column(name = "lat")
    private Double lat;

    @Column(name = "lng")
    private Double lng;

    @Column(length = 2000)
    private String description;

    @Column(name = "star_rating")
    private Integer starRating;

    @Column(name = "min_price", precision = 12, scale = 2)
    private BigDecimal minPrice;

    @Column(length = 3)
    private String currency = "VND";

    @Column(nullable = false)
    private boolean active = true;

    /**
     * P2 (28/08): admin đã sửa tay nội dung khách sạn này → job đồng bộ PMS KHÔNG được đè
     * name/city/description/starRating nữa. Trước đây cứ 15 phút một lần, công sửa tay bị xoá sạch.
     */
    @Column(name = "manual_override", nullable = false)
    private boolean manualOverride = false;

    /** CHANNEL = lay tu he thong ngoai (hotel-pms); DIRECT = vendor tu quan tren Dididi. */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private HotelSource source = HotelSource.CHANNEL;

    /** Chi co gia tri khi source = DIRECT: id cua user (VENDOR) so huu khach san. */
    @Column(name = "vendor_id")
    private Long vendorId;

    // ----- Phan loai phuc vu recommendation -----
    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", length = 20)
    private PropertyType propertyType = PropertyType.HOTEL;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Region region;

    /** Tien ich khach san (bang hotel_amenities). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "hotel_amenities", joinColumns = @JoinColumn(name = "hotel_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "amenity", length = 30)
    private Set<Amenity> amenities = new LinkedHashSet<>();

    /** Dac diem noi bat (bang hotel_tags). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "hotel_tags", joinColumns = @JoinColumn(name = "hotel_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "tag", length = 30)
    private Set<HotelTag> tags = new LinkedHashSet<>();

    public Long getExternalId() { return externalId; }
    public void setExternalId(Long externalId) { this.externalId = externalId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getHouseNumber() { return houseNumber; }
    public void setHouseNumber(String houseNumber) { this.houseNumber = houseNumber; }
    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
    public String getWard() { return ward; }
    public void setWard(String ward) { this.ward = ward; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }
    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getStarRating() { return starRating; }
    public void setStarRating(Integer starRating) { this.starRating = starRating; }
    public BigDecimal getMinPrice() { return minPrice; }
    public void setMinPrice(BigDecimal minPrice) { this.minPrice = minPrice; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isManualOverride() { return manualOverride; }
    public void setManualOverride(boolean manualOverride) { this.manualOverride = manualOverride; }
    public HotelSource getSource() { return source; }
    public void setSource(HotelSource source) { this.source = source; }
    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }

    public PropertyType getPropertyType() { return propertyType; }
    public void setPropertyType(PropertyType propertyType) { this.propertyType = propertyType; }
    public Region getRegion() { return region; }
    public void setRegion(Region region) { this.region = region; }
    public Set<Amenity> getAmenities() { return amenities; }
    public void setAmenities(Set<Amenity> amenities) { this.amenities = amenities; }
    public Set<HotelTag> getTags() { return tags; }
    public void setTags(Set<HotelTag> tags) { this.tags = tags; }

    /** Toa do day du (lat & lng deu khac null) — de UI biet co hien len ban do duoc khong. */
    public boolean hasGeo() { return lat != null && lng != null; }
}
