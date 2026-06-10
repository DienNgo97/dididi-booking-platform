package com.dididi.booking.wishlist.domain;

import com.dididi.booking.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Khach san yeu thich cua nguoi dung. */
@Entity
@Table(name = "wishlist",
        uniqueConstraints = @UniqueConstraint(name = "uk_wishlist_user_hotel", columnNames = {"user_id", "hotel_id"}))
public class Wishlist extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "hotel_id", nullable = false)
    private Long hotelId;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getHotelId() { return hotelId; }
    public void setHotelId(Long hotelId) { this.hotelId = hotelId; }
}
