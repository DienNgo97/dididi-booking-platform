package com.dididi.booking.hotel.domain.entity;

import com.dididi.booking.common.domain.BaseEntity;
import com.dididi.booking.hotel.domain.enums.BedType;
import com.dididi.booking.hotel.domain.enums.RoomView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Loại phòng của khách sạn DIRECT (vendor tự quản). */
@Entity
@Table(name = "room_types")
public class RoomType extends BaseEntity {

    @Column(name = "hotel_id", nullable = false)
    private Long hotelId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private int capacity = 2;

    @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice;

    @Column(nullable = false, length = 3)
    private String currency = "VND";

    @Column(name = "total_rooms", nullable = false)
    private int totalRooms;

    // ----- Thuoc tinh phong phuc vu loc & recommendation -----
    @Enumerated(EnumType.STRING)
    @Column(name = "bed_type", length = 10)
    private BedType bedType;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_view", length = 10)
    private RoomView roomView;

    /** Dien tich phong (m2). */
    @Column(name = "area_sqm")
    private Integer areaSqm;

    public Long getHotelId() { return hotelId; }
    public void setHotelId(Long hotelId) { this.hotelId = hotelId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public int getTotalRooms() { return totalRooms; }
    public void setTotalRooms(int totalRooms) { this.totalRooms = totalRooms; }

    public BedType getBedType() { return bedType; }
    public void setBedType(BedType bedType) { this.bedType = bedType; }
    public RoomView getRoomView() { return roomView; }
    public void setRoomView(RoomView roomView) { this.roomView = roomView; }
    public Integer getAreaSqm() { return areaSqm; }
    public void setAreaSqm(Integer areaSqm) { this.areaSqm = areaSqm; }
}
