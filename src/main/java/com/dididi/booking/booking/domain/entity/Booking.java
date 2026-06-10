package com.dididi.booking.booking.domain.entity;

import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.domain.enums.BookingType;
import com.dididi.booking.common.domain.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings", uniqueConstraints = @UniqueConstraint(name = "uk_bookings_code", columnNames = "public_code"))
public class Booking extends BaseEntity {

    @Column(name = "public_code", nullable = false, length = 20)
    private String publicCode;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BookingType type;

    @Column(length = 300)
    private String title;

    @Column(name = "provider_confirmation", length = 50)
    private String providerConfirmation;

    @Column(name = "check_in")
    private LocalDate checkIn;

    @Column(name = "check_out")
    private LocalDate checkOut;

    @Column(name = "travel_date")
    private LocalDateTime travelDate;

    @Column(nullable = false)
    private int quantity = 1;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency = "VND";

    // DIRECT hotel only: luu de hoan tra ton kho khi huy/hoan tien. CHANNEL/flight = null.
    @Column(name = "room_type_id")
    private Long roomTypeId;

    // Doi tuong duoc dat: hotelId (don HOTEL) hoac flightId (don FLIGHT). Dung cho review/rating.
    @Column(name = "target_id")
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.PENDING_PAYMENT;

    /** Cong ty thanh toan (B2B) - null neu khach le tu tra. */
    @Column(name = "company_id")
    private Long companyId;

    public String getPublicCode() { return publicCode; }
    public void setPublicCode(String publicCode) { this.publicCode = publicCode; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public BookingType getType() { return type; }
    public void setType(BookingType type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getProviderConfirmation() { return providerConfirmation; }
    public void setProviderConfirmation(String providerConfirmation) { this.providerConfirmation = providerConfirmation; }
    public LocalDate getCheckIn() { return checkIn; }
    public void setCheckIn(LocalDate checkIn) { this.checkIn = checkIn; }
    public LocalDate getCheckOut() { return checkOut; }
    public void setCheckOut(LocalDate checkOut) { this.checkOut = checkOut; }
    public LocalDateTime getTravelDate() { return travelDate; }
    public void setTravelDate(LocalDateTime travelDate) { this.travelDate = travelDate; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
    public Long getRoomTypeId() { return roomTypeId; }
    public void setRoomTypeId(Long roomTypeId) { this.roomTypeId = roomTypeId; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
}
