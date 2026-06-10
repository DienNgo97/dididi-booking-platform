package com.dididi.booking.loyalty.domain;

import com.dididi.booking.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** So cai diem thuong (ledger). points: + khi tich, - khi tieu. So du = tong points cua user. */
@Entity
@Table(name = "loyalty_transaction")
public class LoyaltyTransaction extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LoyaltyTxnType type;

    @Column(nullable = false)
    private int points;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(length = 200)
    private String description;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LoyaltyTxnType getType() { return type; }
    public void setType(LoyaltyTxnType type) { this.type = type; }
    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
