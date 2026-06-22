package com.dididi.booking.group.domain.entity;

import com.dididi.booking.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Nhom dat phong (B2C): nguoi to chuc tao 1 nhom cho 1 khach san + khoang ngay, sinh token (link moi).
 * Thanh vien mo link -> them phong cua minh (1 Booking gan group_id) -> tu thanh toan phan minh.
 */
@Entity
@Table(name = "group_bookings", uniqueConstraints = @UniqueConstraint(name = "uk_group_token", columnNames = "token"))
public class GroupBooking extends BaseEntity {

    @Column(name = "token", length = 32, nullable = false)
    private String token;

    @Column(name = "organizer_user_id")
    private Long organizerUserId;

    @Column(name = "hotel_id")
    private Long hotelId;

    @Column(name = "room_type_id")
    private Long roomTypeId;

    @Column(name = "room_name")
    private String roomName;

    @Column(name = "check_in")
    private LocalDate checkIn;

    @Column(name = "check_out")
    private LocalDate checkOut;

    private String title;

    @Column(length = 16)
    private String status = "OPEN";   // OPEN / CLOSED (CLOSED danh cho Phase 2)

    /** Han chot tham gia & thanh toan (null = khong gioi han). */
    @Column(name = "deadline")
    private LocalDateTime deadline;

    /** Chia deu chi phi cho moi nguoi (hien thi phan moi nguoi). */
    @Column(name = "split_even")
    private Boolean splitEven = Boolean.FALSE;

    /** Thoi diem chu nhom bam "Ket thuc chuyen di" (null = chua ket thuc). Cho phep xuat hoa don chia tien. */
    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public Long getOrganizerUserId() { return organizerUserId; }
    public void setOrganizerUserId(Long organizerUserId) { this.organizerUserId = organizerUserId; }

    public Long getHotelId() { return hotelId; }
    public void setHotelId(Long hotelId) { this.hotelId = hotelId; }

    public Long getRoomTypeId() { return roomTypeId; }
    public void setRoomTypeId(Long roomTypeId) { this.roomTypeId = roomTypeId; }

    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }

    public LocalDate getCheckIn() { return checkIn; }
    public void setCheckIn(LocalDate checkIn) { this.checkIn = checkIn; }

    public LocalDate getCheckOut() { return checkOut; }
    public void setCheckOut(LocalDate checkOut) { this.checkOut = checkOut; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getDeadline() { return deadline; }
    public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }

    public boolean isSplitEven() { return Boolean.TRUE.equals(splitEven); }
    public void setSplitEven(boolean splitEven) { this.splitEven = splitEven; }

    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
    public boolean isEnded() { return endedAt != null; }
}
