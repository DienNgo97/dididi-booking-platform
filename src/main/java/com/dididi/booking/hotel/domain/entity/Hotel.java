package com.dididi.booking.hotel.domain.entity;

import com.dididi.booking.common.domain.BaseEntity;
import com.dididi.booking.hotel.domain.enums.HotelSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

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

    @Column(length = 300)
    private String address;

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

    /** CHANNEL = lay tu he thong ngoai (hotel-pms); DIRECT = vendor tu quan tren Dididi. */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private HotelSource source = HotelSource.CHANNEL;

    /** Chi co gia tri khi source = DIRECT: id cua user (VENDOR) so huu khach san. */
    @Column(name = "vendor_id")
    private Long vendorId;

    public Long getExternalId() { return externalId; }
    public void setExternalId(Long externalId) { this.externalId = externalId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
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
    public HotelSource getSource() { return source; }
    public void setSource(HotelSource source) { this.source = source; }
    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }
}
