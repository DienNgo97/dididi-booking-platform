package com.dididi.booking.hotel.domain.entity;

import com.dididi.booking.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Số phòng trống của 1 loại phòng vào 1 ngày (vendor cập nhật hằng ngày). */
@Entity
@Table(name = "room_inventory",
       uniqueConstraints = @UniqueConstraint(name = "uk_inv_roomtype_date", columnNames = {"room_type_id", "inv_date"}))
public class RoomInventory extends BaseEntity {

    @Column(name = "room_type_id", nullable = false)
    private Long roomTypeId;

    @Column(name = "inv_date", nullable = false)
    private LocalDate date;

    @Column(name = "available_rooms", nullable = false)
    private int availableRooms;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    public Long getRoomTypeId() { return roomTypeId; }
    public void setRoomTypeId(Long roomTypeId) { this.roomTypeId = roomTypeId; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public int getAvailableRooms() { return availableRooms; }
    public void setAvailableRooms(int availableRooms) { this.availableRooms = availableRooms; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
}
